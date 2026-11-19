#!/usr/bin/env bash
# dbctl.sh 离线护栏测试 —— 不需要真实数据库连接
#
# 用法：bash sql/postgresql/tests/test_dbctl.sh
# 覆盖：
#   1. 库名分级校验（init 最严；升级/只读命令放行两个正式库名，连字符不泛化）
#   2. 校验库命名规则、dry-run 默认、帮助行为、缺 PGPASSWORD
#   3. 假 psql 失败注入：validation init 全步骤（含 marker/版本登记）失败自动清理，
#      app init 失败保留，dry-run 不触发任何 psql 调用
set -uo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DBCTL="$TEST_DIR/../dbctl.sh"

FAILURES=0

check_rc() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then
    printf 'ok   %s\n' "$label"
  else
    printf 'FAIL %s：期望退出码 %s，实际 %s\n' "$label" "$expected" "$actual" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

check_contains() {
  local needle="$1" haystack="$2" label="$3"
  case "$haystack" in
    *"$needle"*) printf 'ok   %s\n' "$label" ;;
    *)
      printf 'FAIL %s：输出缺少 [%s]\n' "$label" "$needle" >&2
      FAILURES=$((FAILURES + 1))
      ;;
  esac
}

check_not_contains() {
  local needle="$1" haystack="$2" label="$3"
  case "$haystack" in
    *"$needle"*)
      printf 'FAIL %s：输出不应包含 [%s]\n' "$label" "$needle" >&2
      FAILURES=$((FAILURES + 1))
      ;;
    *) printf 'ok   %s\n' "$label" ;;
  esac
}

# 1. 无参数显示帮助且退出码 0
out="$(bash "$DBCTL" 2>&1)"; rc=$?
check_rc 0 "$rc" "无参数退出码 0"
check_contains "子命令" "$out" "无参数输出帮助"
check_contains "ruoyi-vue-pro" "$out" "帮助包含正式库名分级说明"

# 2. 库名白名单：非法字符/注入拒绝（在要求密码之前失败）
bash "$DBCTL" init 'Bad;Name' --new >/dev/null 2>&1; check_rc 1 "$?" "非法库名 Bad;Name 拒绝"
bash "$DBCTL" init 'x;DROP DATABASE yudao_board;--' --new >/dev/null 2>&1; check_rc 1 "$?" "注入库名拒绝"
bash "$DBCTL" init '1abc' --new >/dev/null 2>&1; check_rc 1 "$?" "数字开头库名拒绝"
bash "$DBCTL" init 'ab' --new >/dev/null 2>&1; check_rc 1 "$?" "过短库名拒绝"

# 3. init 最严：正式库与系统库一律拒绝（即使是显式登记的正式库名）
for bad in yudao_board ruoyi-vue-pro postgres template0 template1; do
  bash "$DBCTL" init "$bad" --new >/dev/null 2>&1; check_rc 1 "$?" "init 拒绝 $bad"
done
out="$(bash "$DBCTL" init ruoyi-vue-pro --new 2>&1)"
check_contains "mark-baseline" "$out" "init 正式库报错指向真实升级路径"

# 4. 升级/只读命令放行两个真实正式库名（dry-run 不连库，rc=0 即证明越过名称闸口）
for realdb in ruoyi-vue-pro yudao_board; do
  PGPASSWORD=dummy bash "$DBCTL" migrate "$realdb" >/dev/null 2>&1; check_rc 0 "$?" "migrate 放行正式库 $realdb"
  PGPASSWORD=dummy bash "$DBCTL" mark-baseline "$realdb" >/dev/null 2>&1; check_rc 0 "$?" "mark-baseline 放行正式库 $realdb"
  PGPASSWORD=dummy bash "$DBCTL" seed-system "$realdb" >/dev/null 2>&1; check_rc 0 "$?" "seed-system 放行正式库 $realdb"
done
# status 只读：正式库名越过名称闸口；离线无 psql 时按“库不存在”安全返回，不得是名称类错误
out="$(PGPASSWORD=dummy bash "$DBCTL" status ruoyi-vue-pro 2>&1)"; rc=$?
check_rc 0 "$rc" "status ruoyi-vue-pro 越过名称闸口（离线按不存在处理）"
check_contains "不存在" "$out" "status 离线输出不存在提示而非名称错误"
check_not_contains "不合法" "$out" "status 正式库名不报不合法"
check_not_contains "系统库" "$out" "status 正式库名不报系统库"

# 5. 连字符不泛化：非登记的连字符库名、近似注入串在升级/只读命令同样拒绝
for bad in 'my-db' 'ruoyi-vue-prox' 'ruoyi-vue-pro;DROP TABLE x' 'yudao_board-1' '"ruoyi-vue-pro"'; do
  bash "$DBCTL" migrate "$bad" >/dev/null 2>&1; check_rc 1 "$?" "migrate 拒绝非登记连字符名 $bad"
  bash "$DBCTL" status "$bad" >/dev/null 2>&1; check_rc 1 "$?" "status 拒绝非登记连字符名 $bad"
done
# 系统库在升级命令仍被拒绝
for sysdb in postgres template0 template1; do
  PGPASSWORD=dummy bash "$DBCTL" migrate "$sysdb" >/dev/null 2>&1; check_rc 1 "$?" "migrate 拒绝系统库 $sysdb"
  PGPASSWORD=dummy bash "$DBCTL" mark-baseline "$sysdb" >/dev/null 2>&1; check_rc 1 "$?" "mark-baseline 拒绝系统库 $sysdb"
done

# 6. 校验库命名规则：seed-test / drop-validation 只接受 yudao_validate(_xxx)，正式库仍拒绝
bash "$DBCTL" seed-test mydb >/dev/null 2>&1; check_rc 1 "$?" "seed-test 拒绝非校验库名"
bash "$DBCTL" seed-test yudao_prod >/dev/null 2>&1; check_rc 1 "$?" "seed-test 拒绝 yudao_prod"
bash "$DBCTL" seed-test ruoyi-vue-pro >/dev/null 2>&1; check_rc 1 "$?" "seed-test 拒绝正式库 ruoyi-vue-pro"
bash "$DBCTL" seed-test yudao_board >/dev/null 2>&1; check_rc 1 "$?" "seed-test 拒绝正式库 yudao_board"
bash "$DBCTL" drop-validation yudao_prod >/dev/null 2>&1; check_rc 1 "$?" "drop-validation 拒绝 yudao_prod"
bash "$DBCTL" drop-validation yudao_board >/dev/null 2>&1; check_rc 1 "$?" "drop-validation 拒绝正式库名"
bash "$DBCTL" drop-validation ruoyi-vue-pro >/dev/null 2>&1; check_rc 1 "$?" "drop-validation 拒绝连字符正式库名"

# 7. 写命令默认 dry-run：不带 --run 不报错（不连接数据库），输出计划
out="$(PGPASSWORD=dummy bash "$DBCTL" init yudao_validate_demo --validation 2>&1)"; rc=$?
check_rc 0 "$rc" "init dry-run 退出码 0"
check_contains "[dry-run]   CREATE DATABASE yudao_validate_demo" "$out" "init dry-run 输出建库计划"

out="$(PGPASSWORD=dummy bash "$DBCTL" migrate yudao_validate_demo 2>&1)"; rc=$?
check_rc 0 "$rc" "migrate dry-run 退出码 0"

out="$(PGPASSWORD=dummy bash "$DBCTL" drop-validation yudao_validate_demo 2>&1)"; rc=$?
check_rc 0 "$rc" "drop-validation dry-run 退出码 0"

# 8. 缺少 PGPASSWORD 时给出明确错误
out="$(env -u PGPASSWORD bash "$DBCTL" status yudao_validate_demo 2>&1)"; rc=$?
check_rc 1 "$rc" "缺 PGPASSWORD 拒绝连接"
check_contains "PGPASSWORD" "$out" "缺 PGPASSWORD 提示设置环境变量"

# 9. 假 psql 失败注入：验证 ERR trap 覆盖 validation init 全步骤，且 app 失败保留
FAKE_DIR="$(mktemp -d)"
FAKE_LOG="$FAKE_DIR/calls.log"
FAKE_STATE="$FAKE_DIR/dbs.state"
: > "$FAKE_STATE"
cat > "$FAKE_DIR/psql" <<'FAKE'
#!/usr/bin/env bash
# 最小假 psql：按 FAIL_AT 在指定阶段失败，记录 DROP/CREATE 调用，用状态文件模拟库的存在性
set -u
log="${FAKE_PSQL_LOG:-/dev/null}"
state="${FAKE_PSQL_STATE:-/dev/null}"
sql="" file=""
prev=""
for a in "$@"; do
  case "$prev" in
    -c|-tAc) sql="$a" ;;
    -f) file="$a" ;;
  esac
  prev="$a"
done
case "$sql" in
  *"FROM pg_database"*)
    name="$(printf '%s' "$sql" | sed -n "s/.*datname = '\([^']*\)'.*/\1/p")"
    if [ -n "$name" ] && grep -Fxq "$name" "$state" 2>/dev/null; then echo 1; else echo 0; fi
    exit 0 ;;
  *"CREATE DATABASE"*)
    printf '%s\n' "$sql" >> "$log"
    name="$(printf '%s' "$sql" | sed -n 's/.*CREATE DATABASE "\([^"]*\)".*/\1/p')"
    [ -n "$name" ] && printf '%s\n' "$name" >> "$state"
    exit 0 ;;
  *"DROP DATABASE"*)
    printf '%s\n' "$sql" >> "$log"
    name="$(printf '%s' "$sql" | sed -n 's/.*DROP DATABASE "\([^"]*\)".*/\1/p')"
    if [ -n "$name" ]; then
      grep -Fvx "$name" "$state" > "$state.tmp" 2>/dev/null || true
      mv "$state.tmp" "$state"
    fi
    exit 0 ;;
  *"db_init_marker"*) [ "${FAIL_AT:-}" = "marker" ] && exit 1; exit 0 ;;
  *"schema_migration"*) [ "${FAIL_AT:-}" = "register" ] && exit 1; exit 0 ;;
esac
case "$file" in
  *02_system_seed.sql) [ "${FAIL_AT:-}" = "seed" ] && exit 1 ;;
esac
exit 0
FAKE
chmod +x "$FAKE_DIR/psql"

run_fake() {
  FAIL_AT="${1:-}" FAKE_PSQL_LOG="$FAKE_LOG" FAKE_PSQL_STATE="$FAKE_STATE" \
    PATH="$FAKE_DIR:$PATH" PGPASSWORD=dummy bash "$DBCTL" "${@:2}"
}

# 9.1 SQL 文件阶段失败 → 自动 DROP
: > "$FAKE_LOG"
run_fake seed init yudao_validate_inj --validation --run >/dev/null 2>&1; check_rc 1 "$?" "validation init 种子阶段失败返回 1"
check_contains 'DROP DATABASE "yudao_validate_inj"' "$(cat "$FAKE_LOG")" "种子阶段失败自动 DROP 校验库"

# 9.2 marker 写入失败（原 m1 缺口）→ 同样自动 DROP，无无 marker 残留库
: > "$FAKE_LOG"
run_fake marker init yudao_validate_inj --validation --run >/dev/null 2>&1; check_rc 1 "$?" "validation init marker 阶段失败返回 1"
check_contains 'DROP DATABASE "yudao_validate_inj"' "$(cat "$FAKE_LOG")" "marker 阶段失败自动 DROP 校验库"

# 9.3 版本登记失败（原 m1 缺口）→ 同样自动 DROP
: > "$FAKE_LOG"
run_fake register init yudao_validate_inj --validation --run >/dev/null 2>&1; check_rc 1 "$?" "validation init 版本登记阶段失败返回 1"
check_contains 'DROP DATABASE "yudao_validate_inj"' "$(cat "$FAKE_LOG")" "版本登记阶段失败自动 DROP 校验库"

# 9.4 app 模式失败必须保留库：不得出现任何 DROP
: > "$FAKE_LOG"
run_fake marker init yudao_app_inj --new --run >/dev/null 2>&1; check_rc 1 "$?" "app init marker 阶段失败返回 1"
if [ ! -s "$FAKE_LOG" ] || ! grep -q "DROP DATABASE" "$FAKE_LOG"; then
  printf 'ok   app init 失败保留库（无 DROP 调用）\n'
else
  printf 'FAIL app init 失败不应触发 DROP\n' >&2
  FAILURES=$((FAILURES + 1))
fi
check_contains 'CREATE DATABASE "yudao_app_inj"' "$(cat "$FAKE_LOG")" "app init 确实创建过库"

# 9.5 dry-run 不调用 psql：假 psql 日志必须为空
: > "$FAKE_LOG"
run_fake "" init yudao_validate_dry --validation >/dev/null 2>&1; check_rc 0 "$?" "假 psql 环境 dry-run 退出码 0"
if [ ! -s "$FAKE_LOG" ]; then
  printf 'ok   dry-run 不触发任何 psql 调用\n'
else
  printf 'FAIL dry-run 出现 psql 调用\n' >&2
  FAILURES=$((FAILURES + 1))
fi

rm -rf "$FAKE_DIR"

if [ "$FAILURES" -eq 0 ]; then
  printf '\n全部通过\n'
  exit 0
fi
printf '\n%d 项失败\n' "$FAILURES" >&2
exit 1
