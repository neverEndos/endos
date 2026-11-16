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
         "$RELEASES/20261113-1200-cccc" \
         "$RELEASES/20261113-1300-dddd.tmp.999"

# 场景 1：V1、V2(current)、V3 → 应返回 V1（不能把 V3 当成上一版本）
result="$(previous_release "$RELEASES" "20261113-1100-bbbb")"
check_eq "20261113-1000-aaaa" "$result" "V2 为当前版本时返回严格更小的 V1"

# 场景 2：V1(current) → 没有上一版本，返回空且退出码非 0
result="$(previous_release "$RELEASES" "20261113-1000-aaaa")"
exit_code=$?
check_empty "$result" "V1 为当前版本时无上一版本"
check_eq "1" "$exit_code" "无上一版本时返回非 0"

# 场景 3：不存在 current 时返回最大版本（.tmp.* 临时目录必须被过滤）
result="$(previous_release "$RELEASES" "")"
check_eq "20261113-1200-cccc" "$result" "未指定 current 时返回最大版本（忽略 .tmp.*）"

# 场景 4：new_version 带秒级时间，避免同分钟不同 sha 排序错乱。
result="$(DEPLOY_GIT_SHA=deadbeefcafef00d new_version)"
case "$result" in
  [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]-deadbeef)
    printf 'ok   new_version 使用 YYYYMMDD-HHMMSS-sha8\n'
    ;;
  *)
    printf 'FAIL new_version 格式：[%s]\n' "$result" >&2
    FAILURES=$((FAILURES + 1))
    ;;
esac

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

# prune_releases：只统计/删除非 .tmp.* 版本目录，临时目录保持不动。
PRUNE="$TMP/prune"
mkdir -p "$PRUNE/20261113-1000-aaaa" \
         "$PRUNE/20261113-1100-bbbb" \
         "$PRUNE/20261113-1200-cccc" \
         "$PRUNE/20261113-1300-dddd.tmp.999"
prune_releases "$PRUNE" 2 "20261113-1200-cccc" >/dev/null 2>&1
if [ -d "$PRUNE/20261113-1300-dddd.tmp.999" ]; then
  printf 'ok   prune_releases 不删除 .tmp.* 目录\n'
else
  printf 'FAIL prune_releases 删除了 .tmp.* 目录\n' >&2
  FAILURES=$((FAILURES + 1))
fi

# collector_stable_check：用假 systemctl/journalctl 覆盖核心分支。
FAKE_BIN="$TMP/bin"
mkdir -p "$FAKE_BIN"
cat > "$FAKE_BIN/systemctl" <<'FAKE'
#!/usr/bin/env bash
case "$1" in
  is-active) [ "${FAKE_ACTIVE:-1}" = "1" ] && exit 0 || exit 3 ;;
  show) printf '%s\n' "${FAKE_MAINPID:-4242}" ;;
  *) exit 0 ;;
esac
FAKE
cat > "$FAKE_BIN/journalctl" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "${FAKE_JOURNAL:-}"
FAKE
chmod +x "$FAKE_BIN/systemctl" "$FAKE_BIN/journalctl"
OLD_PATH="$PATH"
PATH="$FAKE_BIN:$PATH"

check_stable() {
  local label="$1" expected="$2"
  shift 2
  local rc=0
  env "$@" bash -c '
source "$1"
collector_stable_check collector 1' _ "$TEST_DIR/../scripts/lib/common.sh" \
    >/dev/null 2>&1 || rc=$?
  check_eq "$expected" "$rc" "$label"
}

check_stable "稳定窗口 active+有主进程+无异常 → 0" 0 \
  FAKE_ACTIVE=1 FAKE_MAINPID=4242 FAKE_JOURNAL="ok"
check_stable "稳定窗口 journal 出现 Traceback → 1" 1 \
  FAKE_ACTIVE=1 FAKE_MAINPID=4242 "FAKE_JOURNAL=Traceback (most recent call last)"
check_stable "稳定窗口 MainPID=0 → 1" 1 \
  FAKE_ACTIVE=1 FAKE_MAINPID=0 FAKE_JOURNAL="ok"
check_stable "稳定窗口服务未 active → 1" 1 \
  FAKE_ACTIVE=0 FAKE_MAINPID=4242 FAKE_JOURNAL="ok"

PATH="$OLD_PATH"

# check_backend_listen_loopback：只用回环地址放行，0.0.0.0/* 或未监听拒绝。
cat > "$FAKE_BIN/ss" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "${FAKE_SS:-}"
FAKE
chmod +x "$FAKE_BIN/ss"

check_listen() {
  local label="$1" expected="$2" expected_rc=0
  shift 2
  SUDO=env PATH="$FAKE_BIN:$PATH" env "$@" bash -c '
source "$1"
check_backend_listen_loopback' _ "$TEST_DIR/../scripts/lib/common.sh" \
    >/dev/null 2>&1 || expected_rc=$?
  check_eq "$expected" "$expected_rc" "$label"
}

HEADER="State Recv-Q Send-Q Local Address:Port Peer Address:Port"
check_listen "48080 仅 127.0.0.1 回环 → 0" 0 \
  "FAKE_SS=$HEADER
LISTEN 0 511 127.0.0.1:48080 0.0.0.0:*"
check_listen "48080 为 [::ffff:127.0.0.1] → 0" 0 \
  "FAKE_SS=$HEADER
LISTEN 0 511 [::ffff:127.0.0.1]:48080 [::]:*"
check_listen "48080 为 0.0.0.0 → 1" 1 \
  "FAKE_SS=$HEADER
LISTEN 0 511 0.0.0.0:48080 0.0.0.0:*"
check_listen "48080 未监听 → 1" 1 "FAKE_SS=$HEADER"

if [ "$FAILURES" -eq 0 ]; then
  printf '\n全部通过\n'
  exit 0
fi
printf '\n%d 项失败\n' "$FAILURES" >&2
exit 1
