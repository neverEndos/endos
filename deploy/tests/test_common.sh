#!/usr/bin/env bash
# deploy/tests/test_common.sh —— deploy/scripts/lib/common.sh 的可重复测试
#
# 用法：bash deploy/tests/test_common.sh
# 覆盖：previous_release 严格取“小于当前版本的最大版本”、switch_symlink 原子切换。
set -uo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/lib/common.sh
source "$TEST_DIR/../scripts/lib/common.sh"

TMP="$(mktemp -d "${TMPDIR:-/tmp}/yudao-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
FAILURES=0

check_eq() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then
    printf 'ok   %s\n' "$label"
  else
    printf 'FAIL %s：期望 [%s]，实际 [%s]\n' "$label" "$expected" "$actual" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

check_empty() {
  local actual="$1" label="$2"
  if [ -z "$actual" ]; then
    printf 'ok   %s\n' "$label"
  else
    printf 'FAIL %s：期望为空，实际 [%s]\n' "$label" "$actual" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

RELEASES="$TMP/releases"
mkdir -p "$RELEASES/20261113-1000-aaaa" \
         "$RELEASES/20261113-1100-bbbb" \
         "$RELEASES/20261113-1200-cccc"

# 场景 1：V1、V2(current)、V3 → 应返回 V1（不能把 V3 当成上一版本）
result="$(previous_release "$RELEASES" "20261113-1100-bbbb")"
check_eq "20261113-1000-aaaa" "$result" "V2 为当前版本时返回严格更小的 V1"

# 场景 2：V1(current) → 没有上一版本，返回空且退出码非 0
result="$(previous_release "$RELEASES" "20261113-1000-aaaa")"
exit_code=$?
check_empty "$result" "V1 为当前版本时无上一版本"
check_eq "1" "$exit_code" "无上一版本时返回非 0"

# 场景 3：不存在 current 时返回最大版本
result="$(previous_release "$RELEASES" "")"
check_eq "20261113-1200-cccc" "$result" "未指定 current 时返回最大版本"

# switch_symlink：临时软链接 + mv -Tf 后指向目标。
# Git Bash 默认不创建原生软链接（ln -s 会复制目录），因此仅在本机支持时执行。
mkdir -p "$TMP/target-v1" "$TMP/target-v2"
if ln -s "$TMP/target-v1" "$TMP/probe-link" 2>/dev/null && [ -L "$TMP/probe-link" ]; then
  ln -s "$TMP/target-v1" "$TMP/current"
  switch_symlink "$TMP/target-v2" "$TMP/current"
  result="$(readlink "$TMP/current")"
  check_eq "$TMP/target-v2" "$result" "switch_symlink 原子替换目标"
else
  printf 'skip switch_symlink（本机文件系统不支持原生软链接）\n'
fi

if [ "$FAILURES" -eq 0 ]; then
  printf '\n全部通过\n'
  exit 0
fi
printf '\n%d 项失败\n' "$FAILURES" >&2
exit 1
