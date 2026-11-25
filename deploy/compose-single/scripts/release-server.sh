#!/usr/bin/env bash
# 开发机后端发布入口：Maven 构建、SCP 上传、板端 apply-server。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/release-common.sh
source "$SCRIPT_DIR/lib/release-common.sh"

usage() {
  cat <<'EOF'
release-server.sh —— 开发机构建并发布后端到单板 Compose

不带任何参数时只显示本帮助并退出 0。

用法：
  bash scripts/release-server.sh --run [选项]

选项：
  --run                 执行发布（防误操作）
  --skip-build          跳过 Maven，复用 yudao-server/target/yudao-server.jar
  --host <地址>         板端地址，默认取 deploy.env 或 192.168.0.31
  --user <用户>         SSH 用户，默认 cat
  --port <端口>         SSH 端口，默认 22
  --key <私钥>          SSH 私钥，默认 ~/.ssh/id_rsa
  --version V           版本号，默认 YYYYMMDD-HHMMSS-<git sha>
  --keep N              板端保留版本数，默认 3
  --timeout <秒>        后端健康检查上限，默认 300
  --dry-run             只打印构建、上传与远端命令，不检查本地产物
  -h, --help            显示本帮助并退出 0

配置文件：scripts/../deploy.env（实际为 deploy/compose-single/deploy.env），
支持 BOARD_HOST、BOARD_USER、BOARD_PORT、SSH_KEY、BOARD_SUDO_PASSWORD。
CLI 优先；不要把真实密码提交到仓库。远端固定调用
/opt/yudao/compose-single/scripts/apply-server.sh，失败会自动回滚。
EOF
}
[ "$#" -gt 0 ] || { usage; exit 0; }
case "${1:-}" in -h|--help) usage; exit 0 ;; esac
RUN_RELEASE=0 SKIP_BUILD=0 VERSION="" KEEP=3 TIMEOUT=300
while [ "$#" -gt 0 ]; do
  case "$1" in
    --run) RUN_RELEASE=1; shift ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --host) [ "$#" -ge 2 ] || release_die "--host 缺少参数"; BOARD_HOST="$2"; shift 2 ;;
    --user) [ "$#" -ge 2 ] || release_die "--user 缺少参数"; BOARD_USER="$2"; shift 2 ;;
    --port) [ "$#" -ge 2 ] || release_die "--port 缺少参数"; BOARD_PORT="$2"; shift 2 ;;
    --key) [ "$#" -ge 2 ] || release_die "--key 缺少参数"; SSH_KEY="$2"; shift 2 ;;
    --version) [ "$#" -ge 2 ] || release_die "--version 缺少参数"; VERSION="$2"; shift 2 ;;
    --keep) [ "$#" -ge 2 ] || release_die "--keep 缺少参数"; KEEP="$2"; shift 2 ;;
    --timeout) [ "$#" -ge 2 ] || release_die "--timeout 缺少参数"; TIMEOUT="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; release_die "未知参数：$1" ;;
  esac
done
[ "$RUN_RELEASE" -eq 1 ] || { usage >&2; release_die "必须显式指定 --run"; }
case "$KEEP" in ""|*[!0-9]*) release_die "--keep 必须是正整数" ;; esac
case "$TIMEOUT" in ""|*[!0-9]*) release_die "--timeout 必须是正整数" ;; esac
[ -n "$VERSION" ] || VERSION="$(release_new_version)"
release_validate_version "$VERSION"
JAR="$REPO_ROOT/yudao-server/target/yudao-server.jar"
if [ "$SKIP_BUILD" -eq 0 ]; then
  if release_is_dry_run; then
    release_info "[dry-run] mvn -B package -pl yudao-server -am -Dmaven.test.skip=true"
  else
    release_info "构建后端 jar"
    (cd "$REPO_ROOT" && env -u BOARD_SUDO_PASSWORD mvn -B package -pl yudao-server -am -Dmaven.test.skip=true)
  fi
fi
release_is_dry_run || [ -f "$JAR" ] || release_die "后端产物不存在：$JAR"
release_init_remote
REMOTE_JAR="/tmp/yudao-server-$VERSION.jar"
release_upload_file "$JAR" "$REMOTE_JAR"
REMOTE_COMPOSE_DIR="${REMOTE_COMPOSE_DIR:-/opt/yudao/compose-single}"
release_remote_sudo "bash $(printf '%q' "$REMOTE_COMPOSE_DIR/scripts/apply-server.sh") --jar $(printf '%q' "$REMOTE_JAR") --version $(printf '%q' "$VERSION") --keep $(printf '%q' "$KEEP") --timeout $(printf '%q' "$TIMEOUT")"
release_cleanup_remote "$REMOTE_JAR"
release_info "后端发布完成：$VERSION"
