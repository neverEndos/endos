#!/usr/bin/env bash
# 新板幂等初始化：软件包、显式目录、Compose 文件、SQL 与可选 collector 单元。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
bootstrap-board.sh —— 单机 Compose 新板初始化

不带任何参数时只显示本帮助并退出 0，不执行任何动作。

用法：
  sudo bash scripts/bootstrap-board.sh --run [选项]

选项：
  --run                 执行初始化；只初始化，不会隐式启动应用
  --start               与 --run 同用时执行 compose config 和 compose up -d
  --skip-packages       跳过 apt 软件包检查/安装
  --env-file <路径>     安装为 /opt/yudao/compose-single/.env（0600，替换已有文件）
  --with-collector      安装 collector.service 与 /etc/yudao/env.remote，不启动 collector
  --service-user <用户> collector 运行用户，默认 sudo 前的用户
  --dry-run             只打印动作
  -h, --help            显示本帮助并退出 0

幂等动作：
  - 缺少时安装 docker.io、docker-compose-v2、python3-psycopg2；
  - 创建 /opt/yudao/{compose-single,releases,data,logs,backups,collector}；
  - 安装 Compose 配置和 PostgreSQL 初始化 SQL，未显式 --env-file 时保留已有 .env；
  - 安装的 shell/SQL/Compose/nginx/systemd 文本按扩展名白名单统一归一化为 LF
    （.env 凭据与 jar、压缩包等二进制不处理），Windows core.autocrlf=true 也无需手工 sed；
  - PostgreSQL 绑定目录属主设为 70:70（postgres:17-alpine）；
  - 只有显式 --start 才启动应用，启动前需先用 apply-*.sh --stage-only 安装产物。
EOF
}
[ "$#" -gt 0 ] || { usage; exit 0; }
case "${1:-}" in -h|--help) usage; exit 0 ;; esac
RUN_INIT=0 START=0 SKIP_PACKAGES=0 WITH_COLLECTOR=0 ENV_FILE=""
SERVICE_USER="${SERVICE_USER:-${SUDO_USER:-$(id -un)}}"
while [ "$#" -gt 0 ]; do
  case "$1" in
    --run) RUN_INIT=1; shift ;;
    --start) START=1; shift ;;
    --skip-packages) SKIP_PACKAGES=1; shift ;;
    --env-file) [ "$#" -ge 2 ] || die "--env-file 缺少参数"; ENV_FILE="$2"; shift 2 ;;
    --with-collector) WITH_COLLECTOR=1; shift ;;
    --service-user) [ "$#" -ge 2 ] || die "--service-user 缺少参数"; SERVICE_USER="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "未知参数：$1" ;;
  esac
done
[ "$RUN_INIT" -eq 1 ] || { usage >&2; die "必须显式指定 --run"; }
[ -z "$ENV_FILE" ] || [ -f "$ENV_FILE" ] || die "环境文件不存在：$ENV_FILE"
if ! is_dry_run; then
  [ "$(id -u)" -eq 0 ] || die "请使用 sudo 执行"
  require_cmd tar
fi
BASE="${YUDAO_BASE:-/opt/yudao}"
TARGET="$BASE/compose-single"
SQL_SOURCE="$COMPOSE_DIR/../../sql/postgresql"
SQL_TARGET="$BASE/sql/postgresql"
[ -d "$SQL_SOURCE" ] || SQL_SOURCE="$SQL_TARGET"

if [ "$SKIP_PACKAGES" -eq 0 ]; then
  PACKAGES=()
  command -v docker >/dev/null 2>&1 || PACKAGES+=(docker.io)
  docker compose version >/dev/null 2>&1 || PACKAGES+=(docker-compose-v2)
  dpkg-query -W -f='${Status}' python3-psycopg2 2>/dev/null | grep -q 'ok installed' || PACKAGES+=(python3-psycopg2)
  if [ "${#PACKAGES[@]}" -gt 0 ]; then
    info "安装软件包：${PACKAGES[*]}"
    run apt-get update
    run apt-get install -y "${PACKAGES[@]}"
  else
    info "Docker、Compose 与 python3-psycopg2 已就绪"
  fi
fi

info "创建目录：$BASE"
run install -d -m 0755 "$BASE" "$TARGET" \
  "$BASE/releases/backend" "$BASE/releases/web" \
  "$BASE/data/postgres" "$BASE/data/redis" "$BASE/data/minio" \
  "$BASE/logs" "$BASE/backups" "$BASE/collector/releases" "$SQL_TARGET"
run chmod 0700 "$BASE/backups" "$BASE/data/postgres"
run chown 70:70 "$BASE/data/postgres"

if [ "$(readlink -f "$COMPOSE_DIR")" != "$(readlink -f "$TARGET")" ]; then
  info "安装 Compose 文件到 $TARGET（不覆盖板端 .env）"
  if is_dry_run; then
    info "[dry-run] tar 复制 $COMPOSE_DIR -> $TARGET，排除 .env/deploy.env"
  else
    tar -C "$COMPOSE_DIR" --exclude='./.env' --exclude='./deploy.env' --exclude='./deploy.env.local' -cf - . \
      | tar -C "$TARGET" -xf -
  fi
fi
# Windows 工作区或非 git 来源包可能带入 CRLF；安装后按白名单统一 LF，
# 覆盖 docker-compose.yml、init/*.sh、nginx/*.conf、scripts/**/*.sh。
normalize_text_lf "$TARGET"

for sql in platform/ruoyi-vue-pro.sql platform/quartz.sql collector/01_schema.sql \
    collector/02_system_seed.sql dbctl.sh; do
  [ -f "$SQL_SOURCE/$sql" ] || die "缺少初始化 SQL：$SQL_SOURCE/$sql"
  if [ "$SQL_SOURCE" = "$SQL_TARGET" ]; then
    info "初始化 SQL 已就位：$SQL_TARGET/$sql"
  else
    run install -D -m 0644 "$SQL_SOURCE/$sql" "$SQL_TARGET/$sql"
  fi
done
# 迁移目录（增量升级用）与校验库演示种子一并安装
if [ "$SQL_SOURCE" != "$SQL_TARGET" ]; then
  run install -d -m 0755 "$SQL_TARGET/migration" "$SQL_TARGET/collector/test"
  for sql in "$SQL_SOURCE"/migration/collector_*_migration.sql \
      "$SQL_SOURCE"/migration/collector_range_fix.sql \
      "$SQL_SOURCE"/collector/test/01_demo_seed.sql; do
    [ -f "$sql" ] || die "缺少迁移/种子 SQL：$sql"
    run install -m 0644 "$sql" "$SQL_TARGET/${sql#"$SQL_SOURCE"/}"
  done
fi
# 清理已废弃的扁平旧脚本（内容已并入 platform/、collector/、migration/）
for legacy in ruoyi-vue-pro.sql quartz.sql collector.sql collector_web_seed.sql \
    collector_alarm_seed.sql collector_aggregate_seed.sql \
    collector_threshold_migration.sql collector_range_fix.sql \
    collector_alarm_migration.sql collector_latest_migration.sql \
    collector_aggregate_migration.sql; do
  run rm -f "$SQL_TARGET/$legacy"
done
# SQL 目录不在 deploy/.gitattributes 覆盖范围内，Windows 检出常为 CRLF；
# 归一化覆盖 dbctl.sh、platform/、collector/、migration/ 下的 sh/sql 文本。
normalize_text_lf "$SQL_TARGET"

if [ -n "$ENV_FILE" ]; then
  run install -m 0600 "$ENV_FILE" "$TARGET/.env"
elif [ ! -f "$TARGET/.env" ]; then
  run install -m 0600 "$TARGET/.env.example" "$TARGET/.env"
  warn "已从模板创建 $TARGET/.env；启动前必须修改 CHANGE_ME 和 MINIO_PUBLIC_DOMAIN"
fi

if [ "$WITH_COLLECTOR" -eq 1 ]; then
  id "$SERVICE_USER" >/dev/null 2>&1 || die "collector 用户不存在：$SERVICE_USER"
  UNIT_SOURCE="$COMPOSE_DIR/../systemd/collector.service"
  [ -f "$UNIT_SOURCE" ] || UNIT_SOURCE="/etc/systemd/system/collector.service"
  [ -f "$UNIT_SOURCE" ] || die "缺少 collector 单元模板：$COMPOSE_DIR/../systemd/collector.service"
  UNIT_RENDERED="/tmp/collector.service.render.$$"
  trap 'rm -f "$UNIT_RENDERED" 2>/dev/null || true' EXIT
  if is_dry_run; then
    info "[dry-run] 渲染 collector.service：User/Group=$SERVICE_USER"
  else
    # 再经一道行尾 CR 清理，避免非 git 来源的单元模板带 CRLF 导致 systemd 解析异常。
    sed -e "s/^User=.*/User=$SERVICE_USER/" -e "s/^Group=.*/Group=$SERVICE_USER/" \
      "$UNIT_SOURCE" | sed 's/\r$//' > "$UNIT_RENDERED"
    install -m 0644 "$UNIT_RENDERED" /etc/systemd/system/collector.service
    set -a; . "$TARGET/.env"; set +a
    install -d -m 0755 /etc/yudao
    umask 077
    {
      printf 'YUDAO_DB_HOST=127.0.0.1\n'
      printf 'YUDAO_DB_PORT=%s\n' "${POSTGRES_PORT:-5432}"
      printf 'YUDAO_DB_NAME=%s\n' "${POSTGRES_DB:-ruoyi-vue-pro}"
      printf 'YUDAO_DB_USERNAME=%s\n' "${POSTGRES_USER:-yudao}"
      printf 'YUDAO_DB_PASSWORD=%s\n' "$POSTGRES_PASSWORD"
      printf 'YUDAO_REDIS_HOST=127.0.0.1\n'
      printf 'YUDAO_REDIS_PASSWORD=%s\n' "$REDIS_PASSWORD"
      printf 'TZ=Asia/Shanghai\n'
    } > /etc/yudao/env.remote.tmp.$$
    mv -f /etc/yudao/env.remote.tmp.$$ /etc/yudao/env.remote
    chmod 600 /etc/yudao/env.remote
    chown -R "$SERVICE_USER:$SERVICE_USER" "$BASE/collector" "$BASE/logs"
    systemctl daemon-reload
  fi
  run systemctl enable collector
  info "collector.service 已安装并设为开机启用，但未启动；发布代码后再启动"
fi

COMPOSE_DIR="$TARGET"
if [ "$START" -eq 1 ]; then
  if grep -q 'CHANGE_ME' "$TARGET/.env"; then
    die ".env 仍包含 CHANGE_ME，拒绝启动"
  fi
  info "校验并启动 Compose"
  run compose config -q
  run compose up -d
else
  info "初始化完成；未启动应用（--start 未指定）"
fi
