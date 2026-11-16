#!/usr/bin/env bash
# 板端前端版本化发布：复制 dist、切 current、Nginx reload 与 HTTP 检查。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
apply-web.sh —— 单机 Compose 前端更新

不带任何参数时只显示本帮助并退出 0。

用法：
  sudo bash scripts/apply-web.sh --dist /tmp/yudao-web-<版本> [选项]

选项：
  --dist <目录>       前端 dist 目录，必须包含 index.html
  --version V         默认取目录名 yudao-web-<版本> 中的版本
  --keep N            保留最近 N 个版本目录，默认 3
  --timeout <秒>      HTTP 200 检查上限，默认 30
  --stage-only        只复制并切 current，不 reload、不检查（用于首次启动前）
  --no-healthcheck    reload 但跳过 HTTP 检查（不推荐）
  --dry-run           只打印动作，不修改文件或服务
  -h, --help          显示本帮助并退出 0

current 是指向 releases/web/<版本> 的相对软链接；Nginx root 为 html/current。
当前版本不允许同版本覆盖，请生成新版本号；健康检查失败会切回更新前软链接。
EOF
}
[ "$#" -gt 0 ] || { usage; exit 0; }
case "${1:-}" in -h|--help) usage; exit 0 ;; esac

DIST="" VERSION="" KEEP=3 TIMEOUT=30 STAGE_ONLY=0 NO_HEALTH=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --dist) [ "$#" -ge 2 ] || die "--dist 缺少参数"; DIST="$2"; shift 2 ;;
    --version) [ "$#" -ge 2 ] || die "--version 缺少参数"; VERSION="$2"; shift 2 ;;
    --keep) [ "$#" -ge 2 ] || die "--keep 缺少参数"; KEEP="$2"; shift 2 ;;
    --timeout) [ "$#" -ge 2 ] || die "--timeout 缺少参数"; TIMEOUT="$2"; shift 2 ;;
    --stage-only) STAGE_ONLY=1; shift ;;
    --no-healthcheck) NO_HEALTH=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "未知参数：$1" ;;
  esac
done
[ -n "$DIST" ] || { usage >&2; die "必须指定 --dist"; }
case "$KEEP" in ""|*[!0-9]*) die "--keep 必须是正整数" ;; esac
[ "$KEEP" -ge 1 ] || die "--keep 至少为 1"
case "$TIMEOUT" in ""|*[!0-9]*) die "--timeout 必须是正整数" ;; esac
[ -z "$VERSION" ] && VERSION="$(basename "$DIST")" && VERSION="${VERSION#yudao-web-}"
valid_version "$VERSION" || die "非法版本号：$VERSION"
[ -f "$DIST/index.html" ] || { is_dry_run || die "dist 缺少 index.html：$DIST"; }
load_compose_env
WEB_DIR="${WEB_RELEASES_DIR:-/opt/yudao/releases/web}"
LINK="$WEB_DIR/current"
TARGET="$WEB_DIR/$VERSION"
STAGE="$WEB_DIR/.${VERSION}.tmp.$$"
CUR_RAW="$(readlink "$LINK" 2>/dev/null || true)"
CUR_VERSION=""
[ -n "$CUR_RAW" ] && CUR_VERSION="$(basename "$CUR_RAW")"
[ "$CUR_VERSION" != "$VERSION" ] || die "版本 $VERSION 已是当前版本，请使用新版本号"
trap 'rm -rf "$STAGE" 2>/dev/null || true' EXIT

info "发布前端版本：$VERSION"
run rm -rf "$STAGE"
run install -d "$WEB_DIR" "$STAGE"
run cp -a "$DIST/." "$STAGE/"
if [ -d "$TARGET" ] && ! is_dry_run; then
  warn "替换非当前版本目录：$TARGET"
  rm -rf "$TARGET"
fi
run mv -Tf "$STAGE" "$TARGET"
switch_symlink "$VERSION" "$LINK"

if [ "$STAGE_ONLY" -eq 1 ]; then
  info "已按 --stage-only 完成落盘，未 reload Nginx"
  exit 0
fi
run compose exec -T web nginx -t
run compose exec -T web nginx -s reload
if [ "$NO_HEALTHCHECK" -eq 1 ]; then
  warn "已按 --no-healthcheck 跳过 HTTP 检查"
elif ! wait_web "$TIMEOUT"; then
  if [ -n "$CUR_RAW" ]; then
    warn "前端检查失败，恢复更新前软链接：$CUR_RAW"
    switch_symlink "$CUR_RAW" "$LINK"
    compose exec -T web nginx -s reload
  fi
  exit 1
fi
prune_releases "$WEB_DIR" "$KEEP" "$VERSION"
info "前端更新完成：$VERSION"
