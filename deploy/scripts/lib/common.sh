#!/usr/bin/env bash
# deploy/scripts/lib/common.sh —— 板端部署脚本公共函数库
#
# 由 deploy/scripts/*.sh 通过 source 引入，不单独执行。
# 约定：调用方先设置 `set -euo pipefail`，并按需设置 DRY_RUN=1。

COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="${DEPLOY_DIR:-$(cd "$COMMON_DIR/../.." && pwd)}"

# ---------- 日志 ----------
log()   { printf '%s\n' "$*"; }
info()  { printf '[信息] %s\n' "$*"; }
warn()  { printf '[警告] %s\n' "$*" >&2; }
error() { printf '[错误] %s\n' "$*" >&2; }
die()   { error "$*"; exit 1; }

# ---------- dry-run ----------
DRY_RUN="${DRY_RUN:-0}"
is_dry_run() { [ "$DRY_RUN" = "1" ]; }

# 打印并执行；dry-run 时只打印（用于不带 sudo 的普通动作）
run() {
  if is_dry_run; then
    printf '[dry-run]'
    printf ' %q' "$@"
    printf '\n'
    return 0
  fi
  "$@"
}

# ---------- sudo ----------
# 服务运行用户；release.sh 以 root 执行板端脚本时用于修正产物属主
SERVICE_USER="${SERVICE_USER:-alientek}"
SERVICE_GROUP="${SERVICE_GROUP:-$SERVICE_USER}"

sudo_run() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    # SUDO 可用环境变量覆盖，例如 SUDO="sudo -n"
    # shellcheck disable=SC2086
    ${SUDO:-sudo} "$@"
  fi
}

run_sudo() {
  if is_dry_run; then
    printf '[dry-run]'
    if [ "$(id -u)" -eq 0 ]; then
      printf ' %q' "$@"
    else
      printf ' %q' "${SUDO:-sudo}" "$@"
    fi
    printf '\n'
    return 0
  fi
  sudo_run "$@"
}

# 以 root 运行脚本后，把新建的部署产物归还给服务用户
fix_owner() {
  [ "$(id -u)" -eq 0 ] || return 0
  id -u "$SERVICE_USER" >/dev/null 2>&1 || return 0
  chown -R "$SERVICE_USER:$SERVICE_GROUP" "$@"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"
}

# ---------- 版本 ----------
# 输出 YYYYMMDD-HHMM-<sha 前 8 位>。
# sha 优先级：DEPLOY_GIT_SHA > 当前 git 仓库 > 源文件 sha256 > 无后缀。
new_version() {
  local src="${1:-}" stamp sha=""
  stamp="$(date +%Y%m%d-%H%M)"
  if [ -n "${DEPLOY_GIT_SHA:-}" ]; then
    sha="${DEPLOY_GIT_SHA:0:8}"
  elif git -C "$DEPLOY_DIR" rev-parse --show-toplevel >/dev/null 2>&1; then
    sha="$(git -C "$DEPLOY_DIR" rev-parse --short=8 HEAD)"
  elif [ -n "$src" ] && [ -f "$src" ] && command -v sha256sum >/dev/null 2>&1; then
    sha="$(sha256sum "$src" | cut -c1-8)"
  fi
  if [ -n "$sha" ]; then
    printf '%s-%s\n' "$stamp" "$sha"
  else
    printf '%s\n' "$stamp"
  fi
}

valid_version() {
  case "${1:-}" in
    ""|.*|*[!A-Za-z0-9._-]*) return 1 ;;
  esac
  return 0
}

# ---------- 软链接 ----------
switch_symlink() {
  local target="$1" link="$2"
  run ln -sfn "$target" "$link"
  if ! is_dry_run && [ ! -L "$link" ]; then
    die "软链接切换失败：$link"
  fi
}

# 输出软链接目标的绝对路径（目标本身可以不存在）
symlink_target_abs() {
  local link="$1" raw
  raw="$(readlink "$link" 2>/dev/null || true)"
  [ -n "$raw" ] || return 1
  case "$raw" in
    /*) printf '%s\n' "$raw" ;;
    *) printf '%s/%s\n' "$(dirname "$link")" "$raw" ;;
  esac
}

# 列出 releases 目录中除 current 之外的最新版本（按名称升序取最后一个）
previous_release() {
  local dir="$1" current="${2:-}" v last=""
  [ -d "$dir" ] || return 1
  while IFS= read -r v; do
    [ -n "$v" ] || continue
    [ "$v" = "$current" ] && continue
    last="$v"
  done < <(find "$dir" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null | sort)
  [ -n "$last" ] || return 1
  printf '%s\n' "$last"
}

# 保留最近 keep 个版本目录，只删除 releases 下的一级子目录。
# 用法：prune_releases <releases 目录> <keep> [受保护版本名]
prune_releases() {
  local dir="$1" keep="${2:-3}" protected="${3:-}"
  local entries=() v count remove removed
  case "$keep" in
    ""|*[!0-9]*) die "保留版本数必须是正整数：$keep" ;;
  esac
  [ "$keep" -ge 1 ] || die "保留版本数至少为 1"
  [ -d "$dir" ] || return 0

  while IFS= read -r v; do
    [ -n "$v" ] && entries+=("$v")
  done < <(find "$dir" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null | sort)

  count=${#entries[@]}
  if [ "$count" -le "$keep" ]; then
    info "版本目录共 $count 个（保留上限 $keep），无需清理"
    return 0
  fi

  remove=$((count - keep))
  removed=0
  for v in "${entries[@]}"; do
    [ "$removed" -ge "$remove" ] && break
    if [ -n "$protected" ] && [ "$v" = "$protected" ]; then
      warn "跳过当前版本目录：$v"
      continue
    fi
    run rm -rf "$dir/$v"
    removed=$((removed + 1))
  done
}

# ---------- 健康检查 ----------
# 等待 TCP 端口可连接；依赖 python3，不依赖 curl/nc
wait_port() {
  local host="$1" port="$2" timeout="${3:-60}" deadline
  deadline=$((SECONDS + timeout))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if python3 -c '
import socket, sys
try:
    s = socket.create_connection((sys.argv[1], int(sys.argv[2])), 1)
    s.close()
except OSError:
    sys.exit(1)
' "$host" "$port" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

# 等待 HTTP 返回指定状态码（默认 200）
wait_http() {
  local url="$1" timeout="${2:-30}" expected="${3:-200}" deadline code
  deadline=$((SECONDS + timeout))
  while [ "$SECONDS" -lt "$deadline" ]; do
    code="$(python3 -c '
import sys, urllib.request
try:
    resp = urllib.request.urlopen(sys.argv[1], timeout=3)
    resp.read(64)
    print(resp.status)
except Exception:
    sys.exit(1)
' "$url" 2>/dev/null || true)"
    if [ "$code" = "$expected" ]; then
      return 0
    fi
    sleep 2
  done
  return 1
}

service_active() {
  systemctl is-active --quiet "$1" 2>/dev/null
}

wait_service_active() {
  local unit="$1" timeout="${2:-30}" deadline
  deadline=$((SECONDS + timeout))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if service_active "$unit"; then
      return 0
    fi
    sleep 2
  done
  return 1
}

# 模板与目标一致时返回 1（未变化），否则安装并返回 0
install_file_if_changed() {
  local src="$1" dst="$2" mode="${3:-0644}"
  if [ -f "$dst" ] && cmp -s "$src" "$dst"; then
    info "已是最新：$dst"
    return 1
  fi
  run_sudo install -m "$mode" "$src" "$dst"
  return 0
}
