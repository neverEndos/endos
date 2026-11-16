#!/usr/bin/env bash
# 单机 Compose 板端脚本公共函数；由调用方 source，不单独执行。
COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="${COMPOSE_DIR:-$(cd "$COMMON_DIR/../.." && pwd)}"
DRY_RUN="${DRY_RUN:-0}"

log() { printf '%s\n' "$*"; }
info() { printf '[信息] %s\n' "$*"; }
warn() { printf '[警告] %s\n' "$*" >&2; }
error() { printf '[错误] %s\n' "$*" >&2; }
die() { error "$*"; exit 1; }
is_dry_run() { [ "$DRY_RUN" = "1" ]; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"; }

load_compose_env() {
  [ -f "$COMPOSE_DIR/.env" ] || die "缺少 $COMPOSE_DIR/.env，请先复制 .env.example 并修改"
  set -a
  # shellcheck disable=SC1091
  . "$COMPOSE_DIR/.env"
  set +a
}

compose() {
  docker compose --project-directory "$COMPOSE_DIR" -f "$COMPOSE_DIR/docker-compose.yml" "$@"
}

run() {
  if is_dry_run; then
    printf '[dry-run]'
    printf ' %q' "$@"
    printf '\n'
  else
    "$@"
  fi
}

valid_version() {
  case "${1:-}" in
    ""|.*|*[!A-Za-z0-9._-]*) return 1 ;;
  esac
}

new_version() {
  local sha=""
  if git -C "$COMPOSE_DIR" rev-parse --show-toplevel >/dev/null 2>&1; then
    sha="-$(git -C "$COMPOSE_DIR" rev-parse --short=8 HEAD)"
  fi
  printf '%s%s\n' "$(date +%Y%m%d-%H%M%S)" "$sha"
}

switch_symlink() {
  local target="$1" link="$2" tmp="$2.tmp.$$"
  if is_dry_run; then
    run ln -s "$target" "$tmp"
    run mv -Tf "$tmp" "$link"
    return 0
  fi
  rm -f -- "$link".tmp.* 2>/dev/null || true
  ln -s "$target" "$tmp"
  mv -Tf "$tmp" "$link"
}

wait_backend() {
  local timeout="${1:-240}" deadline
  if is_dry_run; then
    info "[dry-run] 从 web 容器请求后端 48080，等待上限 ${timeout}s"
    return 0
  fi
  deadline=$((SECONDS + timeout))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if compose exec -T web wget -q -O /dev/null \
      --header='Content-Type: application/json' --post-data='{}' \
      http://yudao-server:48080/admin-api/system/captcha/get >/dev/null 2>&1; then
      info "后端 48080 健康检查通过"
      return 0
    fi
    sleep 3
  done
  error "后端健康检查超时（${timeout}s）"
  return 1
}

wait_web() {
  local timeout="${1:-30}" deadline code
  if is_dry_run; then
    info "[dry-run] 请求 http://127.0.0.1:${WEB_PORT:-80}/，等待上限 ${timeout}s"
    return 0
  fi
  require_cmd python3
  deadline=$((SECONDS + timeout))
  while [ "$SECONDS" -lt "$deadline" ]; do
    code="$(python3 -c 'import sys,urllib.request
try:
 r=urllib.request.urlopen(sys.argv[1],timeout=3); r.read(16); print(r.status)
except Exception:
 sys.exit(1)' "http://127.0.0.1:${WEB_PORT:-80}/" 2>/dev/null || true)"
    if [ "$code" = "200" ]; then
      info "Web HTTP 200 检查通过"
      return 0
    fi
    sleep 2
  done
  error "Web 健康检查超时（${timeout}s）"
  return 1
}

prune_releases() {
  local dir="$1" keep="$2" current="$3"
  local versions=() v remove=0 total
  [ -d "$dir" ] || return 0
  while IFS= read -r v; do
    [ -n "$v" ] && versions+=("$v")
  done < <(find "$dir" -mindepth 1 -maxdepth 1 -type d ! -name '*.tmp.*' -printf '%f\n' | sort)
  total=${#versions[@]}
  [ "$total" -gt "$keep" ] || return 0
  for v in "${versions[@]}"; do
    [ "$total" -gt "$keep" ] || break
    if [ "$v" = "$current" ]; then
      continue
    fi
    info "清理旧版本：$dir/$v"
    run rm -rf "$dir/$v"
    remove=$((remove + 1))
    total=$((total - 1))
  done
}
