#!/usr/bin/env bash
# 板端后端版本化发布：原子替换 jar、切 current.jar、重启、健康检查与失败回滚。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
apply-server.sh —— 单机 Compose 后端更新

不带任何参数时只显示本帮助并退出 0。

用法：
  sudo bash scripts/apply-server.sh --jar /tmp/yudao-server-<版本>.jar [选项]
  sudo bash scripts/apply-server.sh --find [选项]

选项：
  --jar <路径>       待发布 jar；版本默认取文件名 yudao-server-<版本>.jar 中的版本
  --find             自动选择 /tmp 下修改时间最新的 yudao-server-*.jar
  --version V        显式指定版本（字母、数字、点、下划线、连字符）
  --keep N           保留最近 N 个版本目录，默认 3
  --timeout <秒>     后端健康检查上限，默认 300
  --stage-only       只安装 jar 并切 current.jar，不重启、不检查（用于首次启动前）
  --no-healthcheck   重启但跳过 HTTP 健康检查（不推荐）
  --dry-run          只打印动作，不修改文件或服务
  -h, --help         显示本帮助并退出 0

更新流程：
  1. jar 写入 releases/backend/<版本>/yudao-server.jar，current.jar 使用相对软链接；
  2. docker compose restart yudao-server；
  3. 从 web 容器 POST /admin-api/system/captcha/get，要求 HTTP 成功且响应含业务成功标识；
  4. 失败时恢复更新前的软链接；同版本更新则恢复更新前的 jar，并再次重启检查。
EOF
}
[ "$#" -gt 0 ] || { usage; exit 0; }
case "${1:-}" in -h|--help) usage; exit 0 ;; esac

JAR="" VERSION="" KEEP=3 TIMEOUT=300 FIND=0 STAGE_ONLY=0 NO_HEALTHCHECK=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --jar) [ "$#" -ge 2 ] || die "--jar 缺少参数"; JAR="$2"; shift 2 ;;
    --find) FIND=1; shift ;;
    --version) [ "$#" -ge 2 ] || die "--version 缺少参数"; VERSION="$2"; shift 2 ;;
    --keep) [ "$#" -ge 2 ] || die "--keep 缺少参数"; KEEP="$2"; shift 2 ;;
    --timeout) [ "$#" -ge 2 ] || die "--timeout 缺少参数"; TIMEOUT="$2"; shift 2 ;;
    --stage-only) STAGE_ONLY=1; shift ;;
    --no-healthcheck) NO_HEALTHCHECK=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "未知参数：$1" ;;
  esac
done
case "$KEEP" in ""|*[!0-9]*) die "--keep 必须是正整数" ;; esac
[ "$KEEP" -ge 1 ] || die "--keep 至少为 1"
case "$TIMEOUT" in ""|*[!0-9]*) die "--timeout 必须是正整数" ;; esac
[ "$TIMEOUT" -ge 1 ] || die "--timeout 至少为 1"
if [ "$FIND" -eq 1 ]; then
  [ -z "$JAR" ] || die "--find 与 --jar 不能同时使用"
  JAR="$(find /tmp -maxdepth 1 -type f -name 'yudao-server-*.jar' -printf '%T@ %p\n' 2>/dev/null | sort -nr | cut -d' ' -f2- | head -n 1)"
  [ -n "$JAR" ] || die "/tmp 下没有 yudao-server-*.jar"
fi
[ -n "$JAR" ] || { usage >&2; die "必须指定 --jar 或 --find"; }
if [ -z "$VERSION" ]; then
  VERSION="$(basename "$JAR" .jar)"
  VERSION="${VERSION#yudao-server-}"
fi
valid_version "$VERSION" || die "非法版本号：$VERSION"
load_compose_env
BACKEND_DIR="${BACKEND_RELEASES_DIR:-/opt/yudao/releases/backend}"
LINK="$BACKEND_DIR/current.jar"
TARGET_DIR="$BACKEND_DIR/$VERSION"
TARGET_JAR="$TARGET_DIR/yudao-server.jar"
[ -f "$JAR" ] || { is_dry_run || die "jar 不存在：$JAR"; }

CUR_RAW="$(readlink "$LINK" 2>/dev/null || true)"
CUR_VERSION=""
[ -n "$CUR_RAW" ] && CUR_VERSION="$(basename "$(dirname "$CUR_RAW")")"
ROLLBACK_JAR="$BACKEND_DIR/.rollback.jar.$$"
cleanup() { rm -f "$ROLLBACK_JAR" 2>/dev/null || true; }
trap cleanup EXIT

info "发布后端版本：$VERSION"
run install -d "$BACKEND_DIR" "$TARGET_DIR"
if [ "$CUR_VERSION" = "$VERSION" ] && [ -f "$LINK" ] && ! is_dry_run; then
  cp -p "$LINK" "$ROLLBACK_JAR"
fi
run install -m 0644 "$JAR" "$TARGET_JAR.tmp.$$"
run mv -Tf "$TARGET_JAR.tmp.$$" "$TARGET_JAR"
switch_symlink "$VERSION/yudao-server.jar" "$LINK"

if [ "$STAGE_ONLY" -eq 1 ]; then
  info "已按 --stage-only 完成落盘，未重启服务"
  exit 0
fi
run compose restart yudao-server
if [ "$NO_HEALTHCHECK" -eq 1 ]; then
  warn "已按 --no-healthcheck 跳过健康检查"
elif ! wait_backend "$TIMEOUT"; then
  error "新版本 $VERSION 健康检查失败，开始回滚"
  if [ -z "$CUR_RAW" ]; then
    error "没有更新前版本可回滚，请检查 docker compose logs yudao-server"
    exit 1
  fi
  if [ "$CUR_VERSION" = "$VERSION" ] && [ -f "$ROLLBACK_JAR" ]; then
    install -m 0644 "$ROLLBACK_JAR" "$TARGET_JAR.restore.$$"
    mv -Tf "$TARGET_JAR.restore.$$" "$TARGET_JAR"
  else
    switch_symlink "$CUR_RAW" "$LINK"
  fi
  compose restart yudao-server
  if wait_backend "$TIMEOUT"; then
    error "已恢复到更新前版本：${CUR_VERSION:-$CUR_RAW}"
  else
    error "回滚后健康检查仍失败，请人工检查日志"
  fi
  exit 1
fi
prune_releases "$BACKEND_DIR" "$KEEP" "$VERSION"
info "后端更新完成：$VERSION"
