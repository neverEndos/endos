#!/usr/bin/env bash
# 开发机 release-*.sh 公共函数；由调用方 source，不单独执行。
RELEASE_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd "$RELEASE_LIB_DIR/../.." && pwd)"
REPO_ROOT="$(cd "$COMPOSE_DIR/../.." && pwd)"
DEPLOY_ENV_FILE="$COMPOSE_DIR/deploy.env"
DRY_RUN=0
if [ -f "$DEPLOY_ENV_FILE" ]; then
  # 不 set -a，sudo 密码不会进入 Maven/pnpm 子进程环境。
  # shellcheck disable=SC1090
  . "$DEPLOY_ENV_FILE"
fi
BOARD_HOST="${BOARD_HOST:-192.168.0.31}"
BOARD_USER="${BOARD_USER:-cat}"
BOARD_PORT="${BOARD_PORT:-22}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_rsa}"
BOARD_SUDO_PASSWORD="${BOARD_SUDO_PASSWORD:-}"

release_info() { printf '[信息] %s\n' "$*"; }
release_warn() { printf '[警告] %s\n' "$*" >&2; }
release_die() { printf '[错误] %s\n' "$*" >&2; exit 1; }
release_is_dry_run() { [ "$DRY_RUN" = "1" ]; }

release_expand_key() {
  case "$SSH_KEY" in
    "~") SSH_KEY="$HOME" ;;
    "~/"*) SSH_KEY="$HOME/${SSH_KEY#\~/}" ;;
  esac
  SSH_KEY="${SSH_KEY%$'\r'}"
}

release_validate_version() {
  case "${1:-}" in
    ""|.*|*[!A-Za-z0-9._-]*) release_die "非法版本号：${1:-}" ;;
  esac
}

release_new_version() {
  local sha=""
  sha="$(git -C "$REPO_ROOT" rev-parse --short=8 HEAD 2>/dev/null || true)"
  printf '%s%s\n' "$(date +%Y%m%d-%H%M%S)" "${sha:+-$sha}"
}

release_init_remote() {
  release_expand_key
  TARGET="$BOARD_USER@$BOARD_HOST"
  SSH_OPTS=(-o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -p "$BOARD_PORT" -i "$SSH_KEY")
  SCP_OPTS=(-o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -P "$BOARD_PORT" -i "$SSH_KEY")
  if release_is_dry_run; then
    release_info "[dry-run] 目标板：$TARGET"
    return 0
  fi
  command -v ssh >/dev/null 2>&1 || release_die "缺少 ssh"
  command -v scp >/dev/null 2>&1 || release_die "缺少 scp"
  command -v tar >/dev/null 2>&1 || release_die "缺少 tar"
  [ -f "$SSH_KEY" ] || release_die "SSH 私钥不存在：$SSH_KEY"
}

release_upload_file() {
  local src="$1" dst="$2"
  release_info "上传 $src -> $TARGET:$dst"
  if release_is_dry_run; then
    release_info "[dry-run] scp '$src' '$TARGET:$dst'"
    return 0
  fi
  [ -f "$src" ] || release_die "上传源文件不存在：$src"
  scp "${SCP_OPTS[@]}" "$src" "$TARGET:$dst"
}

release_upload_dir() {
  local src="$1" dst="$2"
  release_info "上传目录 $src -> $TARGET:$dst"
  if release_is_dry_run; then
    release_info "[dry-run] tar -C '$src' -czf - . | ssh '$TARGET' 'rm -rf \"$dst\" && mkdir -p \"$dst\" && tar -xzf - -C \"$dst\"'"
    return 0
  fi
  [ -d "$src" ] || release_die "上传源目录不存在：$src"
  ssh "${SSH_OPTS[@]}" "$TARGET" "rm -rf $(printf '%q' "$dst") && mkdir -p $(printf '%q' "$dst")"
  ssh "${SSH_OPTS[@]}" "$TARGET" "tar -xzf - -C $(printf '%q' "$dst")" < <(tar -C "$src" -czf - .)
}

release_remote_sudo() {
  local cmd="$1"
  if release_is_dry_run; then
    release_info "[dry-run] ssh $TARGET sudo $cmd"
    return 0
  fi
  release_info "执行板端命令：$cmd"
  if [ -n "$BOARD_SUDO_PASSWORD" ]; then
    printf '%s\n' "$BOARD_SUDO_PASSWORD" | ssh "${SSH_OPTS[@]}" "$TARGET" "sudo -S -p '' $cmd"
  else
    [ -t 0 ] || release_die "未配置 BOARD_SUDO_PASSWORD 且非交互终端，无法输入 sudo 密码"
    ssh -t "${SSH_OPTS[@]}" "$TARGET" "sudo $cmd"
  fi
}

release_cleanup_remote() {
  if release_is_dry_run; then
    release_info "[dry-run] 清理板端临时路径：$*"
  else
    local q="" p
    for p in "$@"; do q+=" $(printf '%q' "$p")"; done
    ssh "${SSH_OPTS[@]}" "$TARGET" "rm -rf$q" || release_warn "板端临时文件清理失败"
  fi
}

release_find_pnpm() {
  if command -v pnpm >/dev/null 2>&1; then
    printf 'pnpm\n'
  elif command -v pnpm.cmd >/dev/null 2>&1; then
    printf 'pnpm.cmd\n'
  else
    return 1
  fi
}
