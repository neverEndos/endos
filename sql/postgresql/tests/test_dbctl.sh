#!/usr/bin/env bash
# dbctl.sh 离线护栏测试 —— 不需要数据库连接
#
# 用法：bash sql/postgresql/tests/test_dbctl.sh
# 覆盖：库名白名单/注入拒绝、生产库黑名单、校验库命名规则、dry-run 默认、帮助行为。
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

# 1. 无参数显示帮助且退出码 0
out="$(bash "$DBCTL" 2>&1)"; rc=$?
check_rc 0 "$rc" "无参数退出码 0"
check_contains "子命令" "$out" "无参数输出帮助"

# 2. 库名白名单：非法字符/注入拒绝（在要求密码之前失败）
bash "$DBCTL" init 'Bad;Name' --new >/dev/null 2>&1; check_rc 1 "$?" "非法库名 Bad;Name 拒绝"
bash "$DBCTL" init 'x;DROP DATABASE yudao_board;--' --new >/dev/null 2>&1; check_rc 1 "$?" "注入库名拒绝"
bash "$DBCTL" init '1abc' --new >/dev/null 2>&1; check_rc 1 "$?" "数字开头库名拒绝"
bash "$DBCTL" init 'ab' --new >/dev/null 2>&1; check_rc 1 "$?" "过短库名拒绝"

# 3. 生产库黑名单：写操作拒绝（即使库名格式合法）
for bad in yudao_board ruoyi-vue-pro postgres template0 template1; do
  bash "$DBCTL" init "$bad" --new >/dev/null 2>&1; check_rc 1 "$?" "黑名单 $bad 拒绝 init"
  bash "$DBCTL" migrate "$bad" --run >/dev/null 2>&1; check_rc 1 "$?" "黑名单 $bad 拒绝 migrate"
done

# 4. 校验库命名规则：seed-test / drop-validation 只接受 yudao_validate(_xxx)
bash "$DBCTL" seed-test mydb >/dev/null 2>&1; check_rc 1 "$?" "seed-test 拒绝非校验库名"
bash "$DBCTL" seed-test yudao_prod >/dev/null 2>&1; check_rc 1 "$?" "seed-test 拒绝 yudao_prod"
bash "$DBCTL" drop-validation yudao_prod >/dev/null 2>&1; check_rc 1 "$?" "drop-validation 拒绝 yudao_prod"
bash "$DBCTL" drop-validation yudao_board >/dev/null 2>&1; check_rc 1 "$?" "drop-validation 拒绝黑名单库名"

# 5. 写命令默认 dry-run：不带 --run 不报错（不连接数据库），输出计划
out="$(PGPASSWORD=dummy bash "$DBCTL" init yudao_validate_demo --validation 2>&1)"; rc=$?
check_rc 0 "$rc" "init dry-run 退出码 0"
check_contains "[dry-run]   CREATE DATABASE yudao_validate_demo" "$out" "init dry-run 输出建库计划"

out="$(PGPASSWORD=dummy bash "$DBCTL" migrate yudao_validate_demo 2>&1)"; rc=$?
check_rc 0 "$rc" "migrate dry-run 退出码 0"

out="$(PGPASSWORD=dummy bash "$DBCTL" drop-validation yudao_validate_demo 2>&1)"; rc=$?
check_rc 0 "$rc" "drop-validation dry-run 退出码 0"

# 6. 缺少 PGPASSWORD 时给出明确错误
out="$(env -u PGPASSWORD bash "$DBCTL" status yudao_validate_demo 2>&1)"; rc=$?
check_rc 1 "$rc" "缺 PGPASSWORD 拒绝连接"
check_contains "PGPASSWORD" "$out" "缺 PGPASSWORD 提示设置环境变量"

if [ "$FAILURES" -eq 0 ]; then
  printf '\n全部通过\n'
  exit 0
fi
printf '\n%d 项失败\n' "$FAILURES" >&2
exit 1
