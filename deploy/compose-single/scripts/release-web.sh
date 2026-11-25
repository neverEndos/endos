#!/usr/bin/env bash
# 开发机前端发布入口：pnpm 构建、tar/SSH 上传、板端 apply-web。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/release-common.sh
source "$SCRIPT_DIR/lib/release-common.sh"

usage() {
  cat <<'EOF'
release-web.sh —— 开发机构建并发布前端到单板 Compose

不带任何参数时只显示本帮助并退出 0。

用法：
  bash scripts/release-web.sh --run [选项]

选项：
  --run                 执行发布（防误操作）
  --skip-build          跳过 pnpm，复用 apps/web-antdv-next/dist
  --host <地址>         板端地址，默认取 deploy.env 或 192.168.0.31
  --user <用户>         SSH 用户，默认 cat
  --port <端口>         SSH 端口，默认 22
  --key <私钥>          SSH 私钥，默认 ~/.ssh/id_rsa
  --version V           版本号，默认 YYYYMMDD-HHMMSS-<git sha>
  --keep N              板端保留版本数，默认 3
  --dry-run             只打印构建、上传与远端命令，不检查本地产物
  -h, --help            显示本帮助并退出 0

前端目录：yudao-ui/yudao-ui-admin-vben；构建命令 pnpm build:antdv-next。
远端固定调用 /opt/yudao/compose-single/scripts/apply-web.sh。
deploy.env 变量与 release-server.sh 相同，禁止提交真实密码。
EOF
}
[ "$#" -gt 0 ] || { usage; exit 0; }
case "${1:-}" in -h|--help) usage; exit 0 ;; esac
RUN_RELEASE=0 SKIP_BUILD=0 VERSION="" KEEP=3
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
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; release_die "未知参数：$1" ;;
  esac
done
[ "$RUN_RELEASE" -eq 1 ] || { usage >&2; release_die "必须显式指定 --run"; }
case "$KEEP" in ""|*[!0-9]*) release_die "--keep 必须是正整数" ;; esac
[ -n "$VERSION" ] || VERSION="$(release_new_version)"
release_validate_version "$VERSION"
FRONTEND_ROOT="$REPO_ROOT/yudao-ui/yudao-ui-admin-vben"
DIST="$FRONTEND_ROOT/apps/web-antdv-next/dist"
if [ "$SKIP_BUILD" -eq 0 ]; then
  if release_is_dry_run; then
    release_info "[dry-run] (cd $FRONTEND_ROOT && pnpm build:antdv-next)"
  else
    PNPM="$(release_find_pnpm)" || release_die "未找到 pnpm"
    release_info "构建前端 dist"
    (cd "$FRONTEND_ROOT" && env -u BOARD_SUDO_PASSWORD "$PNPM" build:antdv-next)
  fi
fi
release_is_dry_run || [ -f "$DIST/index.html" ] || release_die "前端产物缺少 index.html：$DIST"
release_init_remote
REMOTE_DIST="/tmp/yudao-web-$VERSION"
release_upload_dir "$DIST" "$REMOTE_DIST"
REMOTE_COMPOSE_DIR="${REMOTE_COMPOSE_DIR:-/opt/yudao/compose-single}"
release_remote_sudo "bash $(printf '%q' "$REMOTE_COMPOSE_DIR/scripts/apply-web.sh") --dist $(printf '%q' "$REMOTE_DIST") --version $(printf '%q' "$VERSION") --keep $(printf '%q' "$KEEP")"
release_cleanup_remote "$REMOTE_DIST"
release_info "前端发布完成：$VERSION"
