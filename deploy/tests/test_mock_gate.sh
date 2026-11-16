#!/usr/bin/env bash
# deploy/tests/test_mock_gate.sh —— ensure_backend_mock_disabled 的 fail-closed 测试
#
# 用法：bash deploy/tests/test_mock_gate.sh
# 覆盖：缺失文件/缺失键/空值/true/TruE/"true"/On/Yes/1 拒绝；
#       false/0/off/no 放行；--allow-mock 覆盖 true 与缺失键。
set -uo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/lib/common.sh
source "$TEST_DIR/../scripts/lib/common.sh"

TMP="$(mktemp -d "${TMPDIR:-/tmp}/yudao-mock-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
FAILURES=0

make_env() {
  local name="$1" line="$2"
  printf '%s\n' "$line" > "$TMP/$name.env"
  printf '%s\n' "$TMP/$name.env"
}

check_rc() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then
    printf 'ok   %s\n' "$label"
  else
    printf 'FAIL %s：期望 rc=%s，实际 rc=%s\n' "$label" "$expected" "$actual" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

run_gate() {
  local rc=0
  ensure_backend_mock_disabled "$@" >/dev/null 2>&1 || rc=$?
  printf '%s' "$rc"
}

# 明确关闭的值必须放行。
for value in false 0 off no FALSE Off NO; do
  file="$(make_env "ok-$value" "YUDAO_SECURITY_MOCK_ENABLE=$value")"
  check_rc 0 "$(run_gate "$file")" "放行：YUDAO_SECURITY_MOCK_ENABLE=$value"
done

# 带引号/空白的 false 也必须放行（systemd 会去掉成对引号）。
for value in '"false"' "'0'" '  oFf  '; do
  file="$(make_env "quoted" "YUDAO_SECURITY_MOCK_ENABLE=$value")"
  check_rc 0 "$(run_gate "$file")" "放行：YUDAO_SECURITY_MOCK_ENABLE=$value"
done

# 缺失键、空值、可能开启 mock 的值与未知值一律拒绝。
missing_key="$(make_env missing 'YUDAO_DB_HOST=127.0.0.1')"
check_rc 1 "$(run_gate "$missing_key")" "拒绝：缺少 mock 键"

empty_value="$(make_env empty 'YUDAO_SECURITY_MOCK_ENABLE=')"
check_rc 1 "$(run_gate "$empty_value")" "拒绝：mock 键为空值"

for value in true TruE TRUE '"true"' On ON Yes YES 1 enabled maybe; do
  file="$(make_env "risk-$value" "YUDAO_SECURITY_MOCK_ENABLE=$value")"
  check_rc 1 "$(run_gate "$file")" "拒绝：YUDAO_SECURITY_MOCK_ENABLE=$value"
done

# 文件不存在也按风险拒绝。
check_rc 1 "$(run_gate "$TMP/does-not-exist.env")" "拒绝：env 文件不存在"

# --allow-mock 显式覆盖风险值。
allow_file="$(make_env allow 'YUDAO_SECURITY_MOCK_ENABLE=true')"
check_rc 0 "$(run_gate "$allow_file" --allow-mock)" "--allow-mock 放行 true"
check_rc 0 "$(run_gate "$missing_key" --allow-mock)" "--allow-mock 放行缺失键"

# 未知参数返回 2。
check_rc 2 "$(run_gate "$allow_file" --bogus)" "未知参数返回 2"

if [ "$FAILURES" -eq 0 ]; then
  printf '\n全部通过\n'
  exit 0
fi
printf '\n%d 项失败\n' "$FAILURES" >&2
exit 1
