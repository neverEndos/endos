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
# 输出 YYYYMMDD-HHMMSS-<sha 前 8 位>。
# 秒级时间避免同一分钟内不同 sha 的版本名排序错乱；历史上 YYYYMMDD-HHMM
# 目录仍可参与字典序比较（同一分钟内旧格式视为更早版本）。
# sha 优先级：DEPLOY_GIT_SHA > 当前 git 仓库 > 源文件 sha256 > 无后缀。
new_version() {
  local src="${1:-}" stamp sha=""
  stamp="$(date +%Y%m%d-%H%M%S)"
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
# 原子替换：先在同目录创建临时软链接，再用 mv -Tf 替换，避免 ln -sfn 在目标
# 存在时的短暂空窗或部分失败。
switch_symlink() {
  local target="$1" link="$2" tmp="$2.tmp.$$"
  if is_dry_run; then
    printf '[dry-run]'
    printf ' %q' rm -f "$link.tmp."*
    printf '\n'
    printf '[dry-run]'
    printf ' %q' ln -s "$target" "$tmp"
    printf '\n'
    printf '[dry-run]'
    printf ' %q' mv -Tf "$tmp" "$link"
    printf '\n'
    return 0
  fi
  # 清理上一次异常退出遗留的临时软链接，避免目录被 .tmp.* 垃圾堆积。
  rm -f -- "$link".tmp.* 2>/dev/null || true
  ln -s "$target" "$tmp"
  mv -Tf "$tmp" "$link"
  [ -L "$link" ] || die "软链接切换失败：$link"
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

# 找出严格小于 current 的最大版本名；没有则返回 1（调用方给出明确错误）。
# 版本名按字典序比较（YYYYMMDD-HHMMSS[-sha] 前缀保证时间顺序；旧的
# YYYYMMDD-HHMM 目录视为同一分钟内更早的版本）。
previous_release() {
  local dir="$1" current="${2:-}" v last=""
  [ -d "$dir" ] || return 1
  while IFS= read -r v; do
    [ -n "$v" ] || continue
    [ "$v" = "$current" ] && continue
    if [ -n "$current" ] && [[ ! "$v" < "$current" ]]; then
      # 比 current 新（例如部署过 V3 后又回滚到 V2）不能当作“上一版本”。
      continue
    fi
    if [ -z "$last" ] || [[ "$v" > "$last" ]]; then
      last="$v"
    fi
  # 过滤部署过程中的 .tmp.* 临时目录，避免被当作可回滚版本。
  done < <(find "$dir" -mindepth 1 -maxdepth 1 -type d ! -name '*.tmp.*' \
    -printf '%f\n' 2>/dev/null | sort)
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
  done < <(find "$dir" -mindepth 1 -maxdepth 1 -type d ! -name '*.tmp.*' \
    -printf '%f\n' 2>/dev/null | sort)

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

# ---------- 后端 mock 鉴权门禁 ----------
# 从 systemd EnvironmentFile 读取指定键的最后一次赋值（允许文件 0600 root）。
# 返回值保留原始形态（未去引号/空白），便于调用方做 fail-closed 归一化。
env_file_last_line() {
  local file="$1" key="$2"
  [ -f "$file" ] || return 0
  if is_dry_run || [ -r "$file" ]; then
    # dry-run 不执行 sudo（sudo_run 只会打印命令）；读不到就按缺失键处理。
    grep -E "^${key}=" "$file" 2>/dev/null | tail -n 1 || true
  else
    sudo_run grep -E "^${key}=" "$file" 2>/dev/null | tail -n 1 || true
  fi
}

# 归一化 mock 开关值：去 CR/空白、去 systemd 风格的成对引号、转小写。
normalize_mock_value() {
  local value="$1"
  value="${value%$'\r'}"
  value="$(printf '%s' "$value" | tr -d '[:space:]')"
  case "$value" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
  esac
  value="$(printf '%s' "$value" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
  printf '%s' "$value"
}

# 确保后端不会以 mock 鉴权启动。fail-closed：只有明确 false/0/off/no 才放行；
# 缺失文件/缺失键/空值/未知值/true/1/yes/on 等一律视为“可能开启 mock”。
# 用法：ensure_backend_mock_disabled <env_file> [--allow-mock]
# 返回：0 放行（含显式 --allow-mock），1 拒绝，2 参数错误。
ensure_backend_mock_disabled() {
  local env_file="${1:-}" allow_mock=0 raw value="" reason=""
  shift || true
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --allow-mock) allow_mock=1; shift ;;
      *)
        error "ensure_backend_mock_disabled 未知参数：$1"
        return 2
        ;;
    esac
  done

  if [ -z "$env_file" ] || [ ! -f "$env_file" ]; then
    reason="环境变量文件不存在（${env_file:-未指定}）"
  else
    raw="$(env_file_last_line "$env_file" "YUDAO_SECURITY_MOCK_ENABLE")"
    value="$(normalize_mock_value "${raw#*=}")"
    case "$value" in
      false|0|off|no)
        info "YUDAO_SECURITY_MOCK_ENABLE=$value（mock 鉴权已确认关闭）"
        return 0
        ;;
      "")
        if [ -z "$raw" ]; then
          reason="缺少 YUDAO_SECURITY_MOCK_ENABLE 键（按风险处理）"
        else
          reason="YUDAO_SECURITY_MOCK_ENABLE 为空值（按风险处理）"
        fi
        ;;
      *)
        reason="YUDAO_SECURITY_MOCK_ENABLE=${value}（只有 false/0/off/no 放行）"
        ;;
    esac
  fi

  if [ "$allow_mock" -eq 1 ]; then
    warn "mock 门禁：$reason；已显式 --allow-mock，继续（仅限完全离线的演示环境）"
    return 0
  fi
  error "mock 门禁拒绝：$reason"
  error "mock 会跳过真实鉴权，禁止在局域网可达环境启用；请把 YUDAO_SECURITY_MOCK_ENABLE 改为 false"
  error "确需离线演示请显式追加 --allow-mock"
  return 1
}

# 校验 48080 只监听在回环地址；用于后端重启后确认 --server.address=127.0.0.1 生效。
# 返回 0 通过，1 未监听或出现非回环地址。
check_backend_listen_loopback() {
  local addrs addr bad=0
  if is_dry_run; then
    info "[dry-run] 校验 ss -lnt：48080 仅监听 127.0.0.1"
    return 0
  fi
  addrs="$(sudo_run ss -lnt 2>/dev/null \
    | awk 'NR > 1 && $4 ~ /:48080$/ { print $4 }' || true)"
  if [ -z "$addrs" ]; then
    error "ss -lnt 未发现 48080 监听"
    return 1
  fi
  while IFS= read -r addr; do
    [ -n "$addr" ] || continue
    case "$addr" in
      127.0.0.1:48080|\[::ffff:127.0.0.1\]:48080)
        info "48080 监听地址符合回环要求：$addr"
        ;;
      *)
        error "48080 监听地址不符合回环要求：$addr（期望 127.0.0.1:48080）"
        bad=1
        ;;
    esac
  done <<< "$addrs"
  [ "$bad" -eq 0 ]
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

# 读取指定时间点之后的 journal（普通用户无权限时尝试 sudo）。
journal_since() {
  local unit="$1" since="$2"
  if journalctl -u "$unit" --since "$since" --no-pager -o cat 2>/dev/null; then
    return 0
  fi
  run_sudo journalctl -u "$unit" --since "$since" --no-pager -o cat 2>/dev/null || true
}

# collector 稳定窗口检查：等待 active，持续观察 wait_s 秒，期间服务不得退出，
# journal 不得出现启动失败/异常栈。返回 0 通过，1 失败，2 参数错误。
# 用法：collector_stable_check <unit> <秒> [重启前时间戳]
collector_stable_check() {
  local unit="$1" wait_s="$2" since="${3:-}" deadline pid logtext
  case "$wait_s" in
    ""|*[!0-9]*)
      error "健康检查等待秒数必须是整数：$wait_s"
      return 2
      ;;
  esac
  [ "$wait_s" -ge 1 ] || wait_s=1
  since="${since:-$(date '+%Y-%m-%d %H:%M:%S')}"
  info "collector 稳定窗口检查：等待 ${wait_s} 秒（since=$since）"
  if ! wait_service_active "$unit" "$wait_s"; then
    error "collector 未在 ${wait_s} 秒内进入 active"
    return 1
  fi
  deadline=$((SECONDS + wait_s))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if ! service_active "$unit"; then
      error "collector 在稳定窗口内退出"
      return 1
    fi
    sleep 2
  done
  pid="$(systemctl show -p MainPID --value "$unit" 2>/dev/null || echo 0)"
  if [ "${pid:-0}" = "0" ]; then
    error "collector 服务无主进程（MainPID=0）"
    return 1
  fi
  logtext="$(journal_since "$unit" "$since")"
  if printf '%s\n' "$logtext" | grep -Eq 'PollError|轮询器无法启动|Traceback'; then
    error "collector journal 出现启动失败/异常栈（稳定窗口内）"
    printf '%s\n' "$logtext" | grep -E 'PollError|轮询器无法启动|Traceback' | tail -n 5 >&2
    return 1
  fi
  info "collector 稳定窗口检查通过：服务 active（PID=$pid）"
  return 0
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
