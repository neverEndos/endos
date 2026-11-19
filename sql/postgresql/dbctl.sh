#!/usr/bin/env bash
# dbctl.sh —— yudao PostgreSQL 初始化与迁移控制脚本（采集器/平台）
#
# 子命令：
#   init <库名> --new|--validation [--run]   全新初始化（平台基库 + Quartz + collector
#                                            纯 DDL + 系统种子 + 版本全量登记 + 初始化标记）
#   migrate <库名> [--run]                   增量迁移：按版本登记表跳过已应用迁移，
#                                            执行缺失迁移后刷新系统种子
#   mark-baseline <库名> [--ack-data-repair] [--run]
#                                            旧库基线标记：对每个未登记版本做结构探测，
#                                            全部可证明才登记（拒绝盲标）
#   seed-system <库名> [--run]               仅刷新系统种子（幂等）
#   seed-test <库名> [--run]                 校验库演示种子（强制校验库标记）
#   drop-validation <库名> [--run]           删除校验库（强制校验库标记 + 命名规则）
#   status <库名>                            只读状态：标记、已登记版本、待执行迁移
#
# 通用规则：
#   1. 写操作默认 dry-run，必须显式 --run 才会真正执行；status 只读，无需 --run。
#   2. 数据库名白名单：^[a-z][a-z0-9_]{2,30}$（防注入，psql 无法参数化标识符）。
#   3. 写操作黑名单：ruoyi-vue-pro、yudao_board、postgres、template0、template1。
#   4. 密码只从 PGPASSWORD 环境变量读取，不接受命令行参数。
#   5. 连接参数：PGHOST/PGPORT/PGUSER/PGMAINTDB（默认 127.0.0.1:5432/postgres/postgres）。
#   6. init --validation 任一步失败会自动删除已创建的校验库；init --new 失败保留现场
#      并报告失败步骤，便于排查。
#
# 典型用法（本地临时校验库）：
#   export PGPASSWORD=***
#   ./dbctl.sh init yudao_validate_t1 --validation --run
#   ./dbctl.sh seed-test yudao_validate_t1 --run
#   ./dbctl.sh status yudao_validate_t1
#   ./dbctl.sh drop-validation yudao_validate_t1 --run
#
# 典型用法（已有生产/开发库升级）：
#   ./dbctl.sh mark-baseline yudao_board --run   # 一次性，仅当库从未登记过版本
#   ./dbctl.sh migrate yudao_board --run         # 之后每次发版执行
#
# 环境变量示例：
#   PGHOST=192.168.0.101 PGPORT=5432 PGUSER=yudao PGPASSWORD=*** ./dbctl.sh status ruoyi-vue-pro

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_ROOT="$SCRIPT_DIR"

PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-postgres}"
MAINT_DB="${PGMAINTDB:-postgres}"

RUN=0
ACK_DATA_REPAIR=0
INIT_MODE=""

# 库名白名单与生产库黑名单
IDENT_RE='^[a-z][a-z0-9_]{2,30}$'
VALIDATION_RE='^yudao_validate(_[a-z0-9]+)?$'
PROD_BLACKLIST="ruoyi-vue-pro yudao_board postgres template0 template1"

# 迁移版本清单（顺序即执行顺序；单一事实来源，init/migrate/mark-baseline 共用）
MIGRATIONS=(
    "20261116.1|collector_threshold_migration.sql|ddl|采集器阈值模型迁移（传感器类型/参数模板/通道四档阈值）"
    "20261116.2|collector_range_fix.sql|data_repair|CS1000 量程与四档阈值占位数据修正（纯 DML）"
    "20261117.1|collector_alarm_migration.sql|ddl|报警引擎 v1（sample_id/event_type/活动唯一/状态与游标）"
    "20261117.2|collector_latest_migration.sql|ddl|通道最新值看板表"
    "20261117.3|collector_aggregate_migration.sql|ddl|多级聚合（数据画像/秒分表/valid_count/水位与策略扩展）"
)

log()  { printf '%s\n' "$*"; }
info() { printf '[信息] %s\n' "$*"; }
warn() { printf '[警告] %s\n' "$*" >&2; }
error(){ printf '[错误] %s\n' "$*" >&2; }
die()  { error "$*"; exit 1; }

usage() {
    sed -n '2,38p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 0
}

require_password() {
    [ -n "${PGPASSWORD:-}" ] || die "请先设置 PGPASSWORD 环境变量（密码不接受命令行参数，避免泄露到历史记录）"
}

valid_ident() { [[ "${1:-}" =~ $IDENT_RE ]]; }

is_blacklisted() {
    case " $PROD_BLACKLIST " in
        *" $1 "*) return 0 ;;
        *) return 1 ;;
    esac
}

# 写操作的库名校验：白名单 + 生产库黑名单
check_db_name_for_write() {
    local db="$1"
    valid_ident "$db" || die "库名 [$db] 不合法：仅允许小写字母/数字/下划线，小写字母开头，3~31 位"
    if is_blacklisted "$db"; then
        die "库名 [$db] 在生产库黑名单中，拒绝执行写操作；生产库升级请用 migrate（不会重建对象）"
    fi
}

# 校验库名规则：白名单 + 命名规则 + 黑名单（纵深防御）
check_validation_name() {
    local db="$1"
    valid_ident "$db" || die "库名 [$db] 不合法：仅允许小写字母/数字/下划线，小写字母开头，3~31 位"
    [[ "$db" =~ $VALIDATION_RE ]] || die "库名 [$db] 不符合校验库命名规则 ^yudao_validate(_[a-z0-9]+)?$"
    if is_blacklisted "$db"; then
        die "库名 [$db] 在黑名单中，拒绝执行"
    fi
}

psql_maint() {
    psql -X -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$MAINT_DB" \
        --pset=pager=off "$@"
}

psql_db() {
    local db="$1"; shift
    psql -X -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$db" \
        --pset=pager=off "$@"
}

db_exists() {
    [ "$(psql_maint -tAc "SELECT count(*) FROM pg_database WHERE datname = '$1'")" = "1" ]
}

# 执行 SQL 文件；dry-run 时仅打印
run_file() {
    local db="$1" file="$2" label="$3"
    if [ "$RUN" != "1" ]; then
        printf '[dry-run] %s：psql -d %s -f %s\n' "$label" "$db" "$file"
        return 0
    fi
    info "执行：$label（$file）"
    psql_db "$db" -q -f "$SQL_ROOT/$file"
}

# 确保版本管理表存在（旧库可能没有；与 collector/01_schema.sql 第 0 节保持一致）
ensure_version_tables() {
    local db="$1"
    psql_db "$db" -q <<'SQL'
CREATE TABLE IF NOT EXISTS public.schema_migration (
    version varchar(64) PRIMARY KEY,
    filename varchar(255) NOT NULL,
    kind varchar(16) NOT NULL DEFAULT 'ddl',
    description varchar(255),
    applied_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_by varchar(64) NOT NULL DEFAULT current_user
);
CREATE TABLE IF NOT EXISTS public.db_init_marker (
    id smallint PRIMARY KEY DEFAULT 1,
    purpose varchar(32) NOT NULL,
    created_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_db_init_marker_singleton CHECK (id = 1)
);
SQL
}

migration_recorded() {
    [ "$(psql_db "$1" -tAc "SELECT count(*) FROM public.schema_migration WHERE version = '$2'")" = "1" ]
}

record_migration() {
    local db="$1" version="$2" file="$3" kind="$4" desc="$5"
    if [ "$RUN" != "1" ]; then
        printf '[dry-run] 登记版本 %s（%s）\n' "$version" "$file"
        return 0
    fi
    psql_db "$db" -q -c \
        "INSERT INTO public.schema_migration (version, filename, kind, description) VALUES ('$version', '$file', '$kind', '$desc') ON CONFLICT (version) DO NOTHING;"
}

# 结构探测：返回 0 表示该版本的结构结果可证明已应用
probe_migration() {
    local db="$1" version="$2" result
    case "$version" in
        20261116.1)
            result=$(psql_db "$db" -tAc "
SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='collector_sensor_template')
         AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='collector_sensor_type')
         AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='collector_sensor' AND column_name='sensor_type_id')
         AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='collector_channel' AND column_name='sensor_template_id')
         AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='collector_channel' AND column_name='threshold_low_error')
         AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='collector_channel' AND column_name='threshold_low')
        THEN 't' ELSE 'f' END;") ;;
        20261116.2)
            # 纯 DML 修正：以「不存在任何仍为旧占位量程的 CS1000 number 模板」为已应用证据；
            # 若库中已无 CS1000 演示模板（从未写入或被清理），NOT EXISTS 自然成立，视为无需修正。
            result=$(psql_db "$db" -tAc "
SELECT CASE WHEN NOT EXISTS (
        SELECT 1 FROM public.collector_sensor_template st
        JOIN public.collector_sensor_type t ON t.id = st.sensor_type_id
        WHERE st.deleted = 0 AND t.deleted = 0 AND t.type_code = 'cs1000' AND st.value_kind = 'number'
          AND ((st.channel_code IN ('iso_4','iso_6','iso_14') AND st.value_min = 0 AND st.value_max = 100)
            OR (st.channel_code LIKE 'sae%' AND st.value_min = 0 AND st.value_max = 20))
       ) THEN 't' ELSE 'f' END;") ;;
        20261117.1)
            result=$(psql_db "$db" -tAc "
SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='collector_sample_raw' AND column_name='sample_id')
         AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='collector_alarm' AND column_name='event_type')
         AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='collector_alarm_state')
         AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='collector_alarm_cursor')
        THEN 't' ELSE 'f' END;") ;;
        20261117.2)
            result=$(psql_db "$db" -tAc "
SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='collector_channel_latest')
        THEN 't' ELSE 'f' END;") ;;
        20261117.3)
            result=$(psql_db "$db" -tAc "
SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='collector_aggregate_second')
         AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='collector_aggregate_minute')
         AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='collector_channel' AND column_name='data_profile')
        THEN 't' ELSE 'f' END;") ;;
        *) return 1 ;;
    esac
    [ "$result" = "t" ]
}

# ---------------------------------------------------------------------------
# 子命令实现
# ---------------------------------------------------------------------------

cmd_init() {
    local db="${1:-}"; shift || true
    while [ $# -gt 0 ]; do
        case "$1" in
            --new) INIT_MODE="app" ;;
            --validation) INIT_MODE="validation" ;;
            --run) RUN=1 ;;
            *) die "init：未知参数 $1" ;;
        esac
        shift
    done
    [ -n "$db" ] || die "init：缺少库名"
    [ -n "$INIT_MODE" ] || die "init：必须指定 --new（应用库）或 --validation（校验库）"
    check_db_name_for_write "$db"
    require_password

    local purpose="$INIT_MODE"
    local cleanup_on_fail=0

    if [ "$RUN" != "1" ]; then
        info "[dry-run] init $db（--$INIT_MODE）计划执行："
        printf '[dry-run]   CREATE DATABASE %s\n' "$db"
        printf '[dry-run]   %s\n' "platform/ruoyi-vue-pro.sql → platform/quartz.sql → collector/01_schema.sql → collector/02_system_seed.sql"
        printf '[dry-run]   写入 db_init_marker（purpose=%s）并登记 %d 个迁移版本\n' "$purpose" "${#MIGRATIONS[@]}"
        return 0
    fi

    if db_exists "$db"; then
        die "数据库 $db 已存在，拒绝初始化（避免覆盖）。新装请选择不存在的库名；已有库请使用 migrate / mark-baseline"
    fi
    info "创建数据库 $db"
    psql_maint -q -c "CREATE DATABASE \"$db\""
    cleanup_on_fail=1

    local steps=(
        "platform/ruoyi-vue-pro.sql|平台基库（system/infra，含 DROP+CREATE，仅限空库）"
        "platform/quartz.sql|Quartz 调度表"
        "collector/01_schema.sql|collector 纯 DDL（22 表 + 13 序列 + 版本管理表）"
        "collector/02_system_seed.sql|collector 系统种子（协议/保留策略/菜单/定时任务）"
    )
    local step
    for step in "${steps[@]}"; do
        local file="${step%%|*}" label="${step#*|}"
        if ! run_file "$db" "$file" "$label"; then
            if [ "$purpose" = "validation" ] && [ "$cleanup_on_fail" = "1" ]; then
                warn "校验库初始化失败，自动清理数据库 $db"
                psql_maint -q -c "DROP DATABASE \"$db\"" || warn "清理失败，请人工检查数据库 $db"
                die "init 失败（步骤：$label），校验库已自动删除"
            else
                die "init 失败（步骤：$label），数据库 $db 已保留，请排查后重跑（platform 脚本可重复执行，collector 脚本仅限空库）"
            fi
        fi
    done

    info "写入初始化标记（purpose=$purpose）"
    psql_db "$db" -q -c \
        "INSERT INTO public.db_init_marker (id, purpose) VALUES (1, '$purpose') ON CONFLICT (id) DO NOTHING;"

    info "登记全部迁移版本（全新库 schema 已包含其 DDL 结果）"
    local entry
    for entry in "${MIGRATIONS[@]}"; do
        IFS='|' read -r ver file kind desc <<< "$entry"
        record_migration "$db" "$ver" "$file" "$kind" "全新初始化：schema 与系统种子已包含该版本全部内容"
    done

    info "init 完成：$db（purpose=$purpose，迁移版本 ${#MIGRATIONS[@]}/${#MIGRATIONS[@]} 已登记）"
}

cmd_migrate() {
    local db="${1:-}"; shift || true
    while [ $# -gt 0 ]; do
        case "$1" in
            --run) RUN=1 ;;
            *) die "migrate：未知参数 $1" ;;
        esac
        shift
    done
    [ -n "$db" ] || die "migrate：缺少库名"
    check_db_name_for_write "$db"
    require_password

    if [ "$RUN" != "1" ]; then
        info "[dry-run] migrate $db 计划执行："
        info "[dry-run]   查询 schema_migration，跳过已登记版本"
        local entry ver file kind desc
        for entry in "${MIGRATIONS[@]}"; do
            IFS='|' read -r ver file kind desc <<< "$entry"
            printf '[dry-run]   未登记则执行 migration/%s 并登记版本 %s\n' "$file" "$ver"
        done
        printf '[dry-run]   刷新 collector/02_system_seed.sql（系统种子，幂等）\n'
        return 0
    fi

    db_exists "$db" || die "数据库 $db 不存在"

    local pending=()
    local entry ver file kind desc
    for entry in "${MIGRATIONS[@]}"; do
        IFS='|' read -r ver file kind desc <<< "$entry"
        if migration_recorded "$db" "$ver"; then
            info "版本 $ver 已登记，跳过（$file）"
        else
            pending+=("$entry")
        fi
    done

    if [ "${#pending[@]}" -eq 0 ]; then
        info "所有迁移版本均已登记，无待执行迁移"
    else
        ensure_version_tables "$db"
        local p
        for p in "${pending[@]}"; do
            IFS='|' read -r ver file kind desc <<< "$p"
            info "执行迁移 $ver（$file）：$desc"
            psql_db "$db" -q -f "$SQL_ROOT/migration/$file"
            if ! migration_recorded "$db" "$ver"; then
                record_migration "$db" "$ver" "$file" "$kind" "$desc"
            fi
        done
    fi

    # 迁移后刷新系统种子（菜单/定时任务等增量，幂等）
    run_file "$db" "collector/02_system_seed.sql" "刷新系统种子"
    info "migrate 完成：$db"
}

cmd_mark_baseline() {
    local db="${1:-}"; shift || true
    while [ $# -gt 0 ]; do
        case "$1" in
            --ack-data-repair) ACK_DATA_REPAIR=1 ;;
            --run) RUN=1 ;;
            *) die "mark-baseline：未知参数 $1" ;;
        esac
        shift
    done
    [ -n "$db" ] || die "mark-baseline：缺少库名"
    check_db_name_for_write "$db"
    require_password

    if [ "$RUN" != "1" ]; then
        info "[dry-run] mark-baseline $db 计划执行："
        info "[dry-run]   确保 schema_migration 表存在，逐版本结构探测并登记未登记版本"
        return 0
    fi

    db_exists "$db" || die "数据库 $db 不存在"

    ensure_version_tables "$db"

    local failed=0 recorded=0
    local entry ver file kind desc
    for entry in "${MIGRATIONS[@]}"; do
        IFS='|' read -r ver file kind desc <<< "$entry"
        if migration_recorded "$db" "$ver"; then
            info "版本 $ver 已登记，跳过"
            continue
        fi
        if probe_migration "$db" "$ver"; then
            record_migration "$db" "$ver" "$file" "$kind" "基线标记：结构探测证明已应用（$desc）"
            info "版本 $ver 结构探测通过，已登记为基线（$file）"
            recorded=$((recorded + 1))
            continue
        fi
        # 结构探测未通过：该版本未登记，禁止盲标
        local this_failed=1
        error "版本 $ver（$file）结构探测未通过，未登记"
        case "$ver" in
            20261116.2)
                if [ "$ACK_DATA_REPAIR" = "1" ]; then
                    warn "已确认该数据修正对当前库不适用，按 --ack-data-repair 登记"
                    record_migration "$db" "$ver" "$file" "$kind" "基线标记：--ack-data-repair 确认不适用（$desc）"
                    recorded=$((recorded + 1))
                    this_failed=0
                else
                    error "处理建议（二选一）："
                    error "  a) 对该库执行 migration/collector_range_fix.sql（幂等、单事务）后重试本命令；"
                    error "  b) 若确认该修正对当前库不适用（如演示数据已被清理），加 --ack-data-repair 重试。"
                fi
                ;;
            *)
                error "处理建议：该版本尚未应用，执行 dbctl.sh migrate $db --run 后再复查"
                ;;
        esac
        [ "$this_failed" = "1" ] && failed=1
    done

    if [ "$failed" -ne 0 ]; then
        die "基线标记存在未登记版本（本次成功登记 $recorded 个）。已登记版本不会回滚；请按上方指引处理"
    fi
    info "mark-baseline 完成：$db（本次登记 $recorded 个版本）"
}

cmd_seed_system() {
    local db="${1:-}"; shift || true
    while [ $# -gt 0 ]; do
        case "$1" in
            --run) RUN=1 ;;
            *) die "seed-system：未知参数 $1" ;;
        esac
        shift
    done
    [ -n "$db" ] || die "seed-system：缺少库名"
    check_db_name_for_write "$db"
    require_password

    if [ "$RUN" != "1" ]; then
        printf '[dry-run] psql -d %s -f collector/02_system_seed.sql（系统种子，幂等）\n' "$db"
        return 0
    fi

    db_exists "$db" || die "数据库 $db 不存在"
    run_file "$db" "collector/02_system_seed.sql" "刷新系统种子"
    info "seed-system 完成：$db"
}

cmd_seed_test() {
    local db="${1:-}"; shift || true
    while [ $# -gt 0 ]; do
        case "$1" in
            --run) RUN=1 ;;
            *) die "seed-test：未知参数 $1" ;;
        esac
        shift
    done
    [ -n "$db" ] || die "seed-test：缺少库名"
    check_validation_name "$db"
    require_password
    db_exists "$db" || die "数据库 $db 不存在"

    local purpose
    purpose=$(psql_db "$db" -tAc "SELECT purpose FROM public.db_init_marker WHERE id = 1" 2>/dev/null || true)
    if [ "$purpose" != "validation" ]; then
        die "seed-test 只允许在 dbctl 创建的校验库上执行（db_init_marker.purpose=validation），当前库 purpose=[$purpose]"
    fi

    run_file "$db" "collector/test/01_demo_seed.sql" "校验库演示种子（1 设备/2 传感器/16 通道）"
    info "seed-test 完成：$db"
}

cmd_drop_validation() {
    local db="${1:-}"; shift || true
    while [ $# -gt 0 ]; do
        case "$1" in
            --run) RUN=1 ;;
            *) die "drop-validation：未知参数 $1" ;;
        esac
        shift
    done
    [ -n "$db" ] || die "drop-validation：缺少库名"
    check_validation_name "$db"
    require_password

    if [ "$RUN" != "1" ]; then
        printf '[dry-run] 校验 db_init_marker.purpose=validation 后 DROP DATABASE %s\n' "$db"
        return 0
    fi

    db_exists "$db" || die "数据库 $db 不存在"

    local purpose
    purpose=$(psql_db "$db" -tAc "SELECT purpose FROM public.db_init_marker WHERE id = 1" 2>/dev/null || true)
    if [ "$purpose" != "validation" ]; then
        die "拒绝删除：db_init_marker.purpose=[$purpose] 不是 validation（删库的唯一凭证是 dbctl 校验库标记）"
    fi

    info "删除校验库 $db"
    psql_maint -q -c "DROP DATABASE \"$db\""
    info "drop-validation 完成：$db"
}

cmd_status() {
    local db="${1:-}"; shift || true
    [ -n "$db" ] || die "status：缺少库名"
    valid_ident "$db" || die "库名 [$db] 不合法：仅允许小写字母/数字/下划线，小写字母开头，3~31 位"
    require_password

    if ! db_exists "$db"; then
        info "数据库 $db 不存在（$PGHOST:$PGPORT）"
        return 0
    fi

    local marker versions pending collector_tables
    marker=$(psql_db "$db" -tAc "SELECT purpose FROM public.db_init_marker WHERE id = 1" 2>/dev/null || true)
    [ -n "$marker" ] || marker="（无 db_init_marker，非 dbctl 初始化）"

    log "数据库：$db（$PGHOST:$PGPORT，用户 $PGUSER）"
    log "初始化标记：$marker"

    if [ "$(psql_db "$db" -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='schema_migration'" 2>/dev/null || echo 0)" = "1" ]; then
        versions=$(psql_db "$db" -tAc "SELECT count(*) FROM public.schema_migration")
        log "已登记迁移版本：$versions/${#MIGRATIONS[@]}"
        psql_db "$db" -c "SELECT version, kind, filename, applied_at FROM public.schema_migration ORDER BY version;"
        pending=$(psql_db "$db" -tAc "SELECT string_agg(v, ', ' ORDER BY v) FROM (SELECT unnest(ARRAY['20261116.1','20261116.2','20261117.1','20261117.2','20261117.3']) AS v) s WHERE NOT EXISTS (SELECT 1 FROM public.schema_migration m WHERE m.version = s.v)")
        if [ -n "$pending" ]; then
            warn "待执行迁移：$pending"
        else
            log "待执行迁移：无"
        fi
    else
        log "已登记迁移版本：无 schema_migration 表（可用 mark-baseline 建立基线）"
    fi

    if [ "$(psql_db "$db" -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='collector_protocol'" 2>/dev/null || echo 0)" = "1" ]; then
        collector_tables=$(psql_db "$db" -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'collector\_%'")
        log "collector 表数量：$collector_tables"
        log "协议行：$(psql_db "$db" -tAc "SELECT count(*) FROM public.collector_protocol WHERE deleted=0")（deleted=0）"
        log "设备行：$(psql_db "$db" -tAc "SELECT count(*) FROM public.collector_device WHERE deleted=0")（deleted=0）"
    else
        log "collector 结构：未安装"
    fi
}

# ---------------------------------------------------------------------------
# 入口
# ---------------------------------------------------------------------------

main() {
    [ $# -eq 0 ] && usage
    local sub="$1"; shift
    case "$sub" in
        -h|--help|help) usage ;;
        init)           cmd_init "$@" ;;
        migrate)        cmd_migrate "$@" ;;
        mark-baseline)  cmd_mark_baseline "$@" ;;
        seed-system)    cmd_seed_system "$@" ;;
        seed-test)      cmd_seed_test "$@" ;;
        drop-validation) cmd_drop_validation "$@" ;;
        status)         cmd_status "$@" ;;
        *) usage ;;
    esac
}

main "$@"
