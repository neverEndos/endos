#!/usr/bin/env bash
# deploy/scripts/deploy-frontend.sh —— 板端前端 dist 版本化部署
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

BASE="${YUDAO_BASE:-/opt/yudao}"
WEB_DIR="$BASE/web"
CURRENT="$WEB_DIR/current"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-30}"

usage() {
  cat <<'EOF'
deploy-frontend.sh —— 板端前端 dist 版本化部署（Nginx 静态站点）

不带任何参数时只显示本帮助并退出 0。

用法：
  bash deploy/scripts/deploy-frontend.sh --dist <目录> [选项]

必须：
  --dist <目录>         构建产物目录（必须包含 index.html）
选项：
  --version V           版本号（默认 YYYYMMDD-HHMM；也可用
                        YYYYMMDD-HHMM-<git sha 前 8 位>）
                        取值只能包含字母、数字、点、下划线、连字符
  --keep N              releases 目录保留版本数（默认 3，至少 1）
  --dry-run            只打印动作，不复制、不切软链接、不重载 Nginx
  -h, --help           显示本帮助并退出 0

部署流程与副作用：
  1. 复制 dist 内容到 /opt/yudao/web/releases/<版本>/；
  2. 切换软链接 /opt/yudao/web/current 指向新版本（Nginx root）；
  3. sudo nginx -t 校验配置，sudo systemctl reload nginx；
  4. 请求 http://127.0.0.1/，要求返回 200，否则非 0 退出；
  5. 成功后清理旧版本目录，保留最近 --keep 个。

前置条件：
  - 已执行 bootstrap-board.sh --run（Nginx 站点与目录就绪）；
  - 以 alientek 或 root 执行；nginx -t/reload 需要 sudo 权限。

示例：
  bash deploy/scripts/deploy-frontend.sh --dist /tmp/yudao-web-20261113-2200
  bash deploy/scripts/deploy-frontend.sh --dist ./dist --version 20261113-2200-72ad2427
  bash deploy/scripts/deploy-frontend.sh --dist ./dist --dry-run

说明：
  - Nginx root 为 /opt/yudao/web/current，升级只需切换软链接。
  - 开发机通常通过 release.sh frontend 调用本脚本。
EOF
}

if [ "$#" -eq 0 ]; then
  usage
  exit 0
fi

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac

DIST=""
VERSION=""
KEEP=3

while [ "$#" -gt 0 ]; do
  case "$1" in
    --dist)
      [ "$#" -ge 2 ] || { usage >&2; die "--dist 缺少参数"; }
      DIST="$2"
      shift 2
      ;;
    --version)
      [ "$#" -ge 2 ] || { usage >&2; die "--version 缺少参数"; }
      VERSION="$2"
      shift 2
      ;;
    --keep)
      [ "$#" -ge 2 ] || { usage >&2; die "--keep 缺少参数"; }
      KEEP="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      die "未知参数：$1"
      ;;
  esac
done

[ -n "$DIST" ] || { usage >&2; die "缺少必需参数 --dist"; }
case "$KEEP" in
  ""|*[!0-9]*) usage >&2; die "--keep 必须是正整数：$KEEP" ;;
esac
[ "$KEEP" -ge 1 ] || { usage >&2; die "--keep 至少为 1"; }

if [ ! -d "$DIST" ]; then
  if is_dry_run; then
    warn "dist 目录不存在（dry-run 继续）：$DIST"
  else
    die "dist 目录不存在：$DIST"
  fi
elif [ ! -f "$DIST/index.html" ]; then
  if is_dry_run; then
    warn "dist 中缺少 index.html（dry-run 继续）：$DIST"
  else
    die "dist 中缺少 index.html：$DIST"
  fi
fi

if [ -d "$DIST" ]; then
  DIST="$(cd "$DIST" && pwd)"
fi

if [ -z "$VERSION" ]; then
  VERSION="$(new_version)"
fi
valid_version "$VERSION" || { usage >&2; die "非法版本号：$VERSION"; }

[ -d "$WEB_DIR/releases" ] || die "缺少目录 $WEB_DIR/releases，请先运行 bootstrap-board.sh --run"

export DRY_RUN
TARGET_DIR="$WEB_DIR/releases/$VERSION"

info "前端部署版本：$VERSION"
info "源 dist：$DIST"

# 同版本重部署拒绝覆盖（current 指向的版本正在被 Nginx 使用）。
CUR_TARGET=""
CUR_VERSION=""
if [ -L "$CURRENT" ]; then
  CUR_TARGET="$(symlink_target_abs "$CURRENT" 2>/dev/null || true)"
  if [ -n "$CUR_TARGET" ] && [ -e "$CUR_TARGET" ]; then
    CUR_VERSION="$(basename "$CUR_TARGET")"
  fi
fi
if [ "$VERSION" = "$CUR_VERSION" ]; then
  die "版本 $VERSION 已是当前版本，拒绝覆盖；请使用新的 --version 或先回滚"
fi

# ---------- 1. 复制到临时目录并原子切换 ----------
STAGE_DIR="$TARGET_DIR.tmp.$$"
if [ -d "$TARGET_DIR" ]; then
  warn "版本目录已存在（非当前版本），将替换：$TARGET_DIR"
fi
if is_dry_run; then
  printf '[dry-run]'
  printf ' %q' rm -rf "$STAGE_DIR"
  printf '\n'
  printf '[dry-run]'
  printf ' %q' install -d "$STAGE_DIR"
  printf '\n'
  printf '[dry-run]'
  printf ' %q' cp -a "$DIST/." "$STAGE_DIR/"
  printf '\n'
  printf '[dry-run]'
  printf ' %q' rm -rf "$TARGET_DIR"
  printf '\n'
  printf '[dry-run]'
  printf ' %q' mv -Tf "$STAGE_DIR" "$TARGET_DIR"
  printf '\n'
else
  trap 'rm -rf "$STAGE_DIR" 2>/dev/null || true' EXIT
  rm -rf "$STAGE_DIR"
  install -d "$STAGE_DIR"
  cp -a "$DIST/." "$STAGE_DIR/"
  fix_owner "$STAGE_DIR"
  [ -f "$STAGE_DIR/index.html" ] || die "复制后缺少 index.html，部署中止"
  rm -rf "$TARGET_DIR"
  mv -Tf "$STAGE_DIR" "$TARGET_DIR"
fi

# ---------- 2. 切换软链接 ----------
info "切换软链接：$CURRENT -> releases/$VERSION"
switch_symlink "releases/$VERSION" "$CURRENT"

# ---------- 3. Nginx 校验与重载 ----------
info "校验并重载 Nginx"
run_sudo nginx -t
run_sudo systemctl reload nginx

# ---------- 4. 健康检查 ----------
if is_dry_run; then
  info "[dry-run] 跳过请求 http://127.0.0.1/ 检查 200"
elif wait_http "http://127.0.0.1/" "$HEALTH_TIMEOUT" 200; then
  info "前端健康检查通过：http://127.0.0.1/ 返回 200"
else
  error "前端健康检查失败：http://127.0.0.1/ 未在 ${HEALTH_TIMEOUT} 秒内返回 200"
  error "可用 deploy/scripts/rollback.sh frontend 切回上一版本"
  exit 1
fi

# ---------- 5. 清理旧版本 ----------
prune_releases "$WEB_DIR/releases" "$KEEP" "$VERSION"

info "前端部署完成：$VERSION"
