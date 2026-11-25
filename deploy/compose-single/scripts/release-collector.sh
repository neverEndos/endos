#!/usr/bin/env bash
# 开发机 collector 发布入口；collector 继续以宿主机 systemd 运行。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/release-common.sh
source "$SCRIPT_DIR/lib/release-common.sh"

usage() {
  cat <<'EOF'
release-collector.sh —— 发布宿主机 systemd collector

不带任何参数时只显示本帮助并退出 0。

用法：
  bash scripts/release-collector.sh --run [选项]

选项：
  --run              执行发布
  --host <地址>      板端地址，默认取 deploy.env 或 192.168.0.31
  --user <用户>      SSH 与 collector 运行用户，默认 cat
  --port <端口>      SSH 端口，默认 22
  --key <私钥>       SSH 私钥，默认 ~/.ssh/id_rsa
  --version V        默认 YYYYMMDD-HHMMSS-<git sha>
  --keep N           保留版本数，默认 3
  --health-wait <秒> collector 稳定窗口，默认 30
  --dry-run          只打印上传与远端命令
  -h, --help         显示本帮助并退出 0

复用 deploy/scripts/deploy-collector.sh，上传 deploy/scripts 与 deploy/systemd。
前置条件：bootstrap-board.sh --run --with-collector，且本机 PG 已启动。
collector 不进入容器，使用 /dev/ttyS4 与 127.0.0.1:5432。
EOF
}
[ "$#" -gt 0 ] || { usage; exit 0; }
case "${1:-}" in -h|--help) usage; exit 0 ;; esac
RUN_RELEASE=0 VERSION="" KEEP=3 HEALTH_WAIT=30
while [ "$#" -gt 0 ]; do
  case "$1" in
    --run) RUN_RELEASE=1; shift ;;
    --host) [ "$#" -ge 2 ] || release_die "--host 缺少参数"; BOARD_HOST="$2"; shift 2 ;;
    --user) [ "$#" -ge 2 ] || release_die "--user 缺少参数"; BOARD_USER="$2"; shift 2 ;;
    --port) [ "$#" -ge 2 ] || release_die "--port 缺少参数"; BOARD_PORT="$2"; shift 2 ;;
    --key) [ "$#" -ge 2 ] || release_die "--key 缺少参数"; SSH_KEY="$2"; shift 2 ;;
    --version) [ "$#" -ge 2 ] || release_die "--version 缺少参数"; VERSION="$2"; shift 2 ;;
    --keep) [ "$#" -ge 2 ] || release_die "--keep 缺少参数"; KEEP="$2"; shift 2 ;;
    --health-wait) [ "$#" -ge 2 ] || release_die "--health-wait 缺少参数"; HEALTH_WAIT="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; release_die "未知参数：$1" ;;
  esac
done
[ "$RUN_RELEASE" -eq 1 ] || { usage >&2; release_die "必须显式指定 --run"; }
case "$KEEP" in ""|*[!0-9]*) release_die "--keep 必须是正整数" ;; esac
case "$HEALTH_WAIT" in ""|*[!0-9]*) release_die "--health-wait 必须是正整数" ;; esac
[ -n "$VERSION" ] || VERSION="$(release_new_version)"
release_validate_version "$VERSION"
release_is_dry_run || [ -f "$REPO_ROOT/collector/main.py" ] || release_die "collector/main.py 不存在"
release_init_remote
REMOTE_STAGE="/tmp/yudao-deploy"
REMOTE_COLLECTOR="/tmp/yudao-collector-$VERSION"
release_upload_dir "$REPO_ROOT/deploy/scripts" "$REMOTE_STAGE/scripts"
release_upload_dir "$REPO_ROOT/deploy/systemd" "$REMOTE_STAGE/systemd"
release_upload_dir "$REPO_ROOT/collector" "$REMOTE_COLLECTOR"
release_remote_sudo "SERVICE_USER=$(printf '%q' "$BOARD_USER") bash $(printf '%q' "$REMOTE_STAGE/scripts/deploy-collector.sh") --src $(printf '%q' "$REMOTE_COLLECTOR") --version $(printf '%q' "$VERSION") --keep $(printf '%q' "$KEEP") --health-wait $(printf '%q' "$HEALTH_WAIT")"
release_cleanup_remote "$REMOTE_COLLECTOR" "$REMOTE_STAGE"
release_info "collector 发布完成：$VERSION"
