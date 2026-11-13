#!/usr/bin/env bash
# deploy/scripts/deploy-collector.sh —— 板端 collector 代码版本化部署
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

BASE="${YUDAO_BASE:-/opt/yudao}"
COLLECTOR_DIR="$BASE/collector"
LINK="$COLLECTOR_DIR/current"
UNIT_NAME="collector"
UNIT_DST="/etc/systemd/system/collector.service"

usage() {
  cat <<'EOF'
deploy-collector.sh —— 板端 collector 代码版本化部署

不带任何参数时只显示本帮助并退出 0。

用法：
  bash deploy/scripts/deploy-collector.sh --src <目录> [选项]

必须：
  --src <目录>          collector 源码目录（必须包含 main.py）
选项：
  --version V           版本号（默认 YYYYMMDD-HHMM；也可用
                        YYYYMMDD-HHMM-<git sha 前 8 位>）
                        取值只能包含字母、数字、点、下划线、连字符
  --keep N              releases 目录保留版本数（默认 3，至少 1）
  --health-wait <秒>    collector 稳定窗口（默认 30 秒）：期间服务必须保持 active，
                        且 journal 不得出现 PollError/轮询器无法启动/Traceback
  --dry-run            只打印动作，不复制、不切软链接、不重启
  -h, --help           显示本帮助并退出 0

部署流程与副作用：
  1. 复制 src 内容到临时目录并原子切换到 /opt/yudao/collector/releases/<版本>/；
     同版本重部署直接拒绝，避免覆盖正在运行的版本；
  2. 切换软链接 /opt/yudao/collector/current 指向新版本（临时软链接 + mv -Tf）；
  3. 若 deploy/systemd/collector.service 与已装单元不同，安装并 daemon-reload；
  4. sudo systemctl restart collector，执行 --health-wait 秒稳定窗口检查；
  5. 重启失败或稳定窗口失败时自动切回上一版本并重启，然后以非 0 退出；
  6. 成功后清理旧版本目录，保留最近 --keep 个。

前置条件：
  - 已执行 bootstrap-board.sh --run（目录、单元、串口权限就绪）；
  - 同一时间只能有一个进程占用 /dev/ttyS4；restart 会先停止旧进程；
  - 以 alientek 或 root 执行；systemctl 需要 sudo 权限。

示例：
  bash deploy/scripts/deploy-collector.sh --src /tmp/yudao-collector-20261113-2200
  bash deploy/scripts/deploy-collector.sh --src ./collector \
      --version 20261113-2200-72ad2427
  bash deploy/scripts/deploy-collector.sh --src ./collector --dry-run

说明：
  - /var/log/yudao/ 下的 JSONL 与日志不随代码目录删除。
  - 开发机通常通过 release.sh collector 调用本脚本。
EOF
}

if [ "$#" -eq 0 ]; then
  usage
  exit 0
fi

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac

SRC=""
VERSION=""
KEEP=3
HEALTH_WAIT=30

while [ "$#" -gt 0 ]; do
  case "$1" in
    --src)
      [ "$#" -ge 2 ] || { usage >&2; die "--src 缺少参数"; }
      SRC="$2"
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
    --health-wait)
      [ "$#" -ge 2 ] || { usage >&2; die "--health-wait 缺少参数"; }
      HEALTH_WAIT="$2"
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

[ -n "$SRC" ] || { usage >&2; die "缺少必需参数 --src"; }
case "$KEEP" in
  ""|*[!0-9]*) usage >&2; die "--keep 必须是正整数：$KEEP" ;;
esac
[ "$KEEP" -ge 1 ] || { usage >&2; die "--keep 至少为 1"; }
case "$HEALTH_WAIT" in
  ""|*[!0-9]*) usage >&2; die "--health-wait 必须是正整数：$HEALTH_WAIT" ;;
esac
[ "$HEALTH_WAIT" -ge 1 ] || { usage >&2; die "--health-wait 至少为 1"; }

if [ ! -d "$SRC" ]; then
  if is_dry_run; then
    warn "源码目录不存在（dry-run 继续）：$SRC"
  else
    die "源码目录不存在：$SRC"
  fi
elif [ ! -f "$SRC/main.py" ]; then
  if is_dry_run; then
    warn "源码目录缺少 main.py（dry-run 继续）：$SRC"
  else
    die "源码目录缺少 main.py：$SRC"
  fi
fi

if [ -d "$SRC" ]; then
  SRC="$(cd "$SRC" && pwd)"
fi

if [ -z "$VERSION" ]; then
  VERSION="$(new_version)"
fi
valid_version "$VERSION" || { usage >&2; die "非法版本号：$VERSION"; }

[ -d "$COLLECTOR_DIR/releases" ] || die "缺少目录 $COLLECTOR_DIR/releases，请先运行 bootstrap-board.sh --run"

export DRY_RUN
TARGET_DIR="$COLLECTOR_DIR/releases/$VERSION"

info "collector 部署版本：$VERSION"
info "源码目录：$SRC"

# 记录当前（上一）版本，供启动失败时回滚；同版本重部署拒绝覆盖。
CUR_TARGET=""
CUR_VERSION=""
PREV_TARGET=""
if [ -L "$LINK" ]; then
  CUR_TARGET="$(symlink_target_abs "$LINK" 2>/dev/null || true)"
  if [ -n "$CUR_TARGET" ] && [ -e "$CUR_TARGET" ]; then
    PREV_TARGET="$CUR_TARGET"
    CUR_VERSION="$(basename "$CUR_TARGET")"
  fi
fi
if [ "$VERSION" = "$CUR_VERSION" ]; then
  die "版本 $VERSION 已是当前版本，拒绝覆盖；请使用新的 --version 或先回滚"
fi

rollback_collector() {
  local reason="$1" since
  if [ -z "$PREV_TARGET" ]; then
    error "$reason；没有可回滚的上一版本，请检查 journalctl -u $UNIT_NAME"
    return 1
  fi
  warn "$reason，自动回滚到上一版本：$PREV_TARGET"
  switch_symlink "$PREV_TARGET" "$LINK"
  since="$(date '+%Y-%m-%d %H:%M:%S')"
  if ! run_sudo systemctl restart "$UNIT_NAME"; then
    error "回滚到上一版本后重启失败，请人工检查 journalctl -u $UNIT_NAME"
    return 1
  fi
  if ! collector_stable_check "$UNIT_NAME" "$HEALTH_WAIT" "$since"; then
    error "回滚到上一版本后稳定窗口检查失败"
    return 1
  fi
  info "已回滚到上一版本：$PREV_TARGET"
  return 0
}

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
  printf ' %q' cp -a "$SRC/." "$STAGE_DIR/"
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
  cp -a "$SRC/." "$STAGE_DIR/"
  # 清掉本机运行时产生的字节码缓存，避免带入版本目录
  find "$STAGE_DIR" -type d -name __pycache__ -prune -exec rm -rf {} + 2>/dev/null || true
  fix_owner "$STAGE_DIR"
  [ -f "$STAGE_DIR/main.py" ] || die "复制后缺少 main.py，部署中止"
  rm -rf "$TARGET_DIR"
  mv -Tf "$STAGE_DIR" "$TARGET_DIR"
fi

# ---------- 2. 切换软链接 ----------
info "切换软链接：$LINK -> releases/$VERSION"
switch_symlink "releases/$VERSION" "$LINK"

# ---------- 3. systemd 单元（如有变化） ----------
UNIT_TEMPLATE="$DEPLOY_DIR/systemd/collector.service"
if [ -f "$UNIT_TEMPLATE" ]; then
  if [ -f "$UNIT_DST" ] && cmp -s "$UNIT_TEMPLATE" "$UNIT_DST"; then
    info "systemd 单元未变化：$UNIT_DST"
  else
    info "安装变更后的 systemd 单元：$UNIT_DST"
    run_sudo install -m 0644 "$UNIT_TEMPLATE" "$UNIT_DST"
    info "执行 systemd daemon-reload"
    run_sudo systemctl daemon-reload
  fi
else
  info "未随包提供 systemd 单元模板，跳过单元更新"
fi

# ---------- 4. 重启并做稳定窗口检查 ----------
info "重启 collector（会先停止旧进程，释放 /dev/ttyS4）"
RESTART_SINCE="$(date '+%Y-%m-%d %H:%M:%S')"
if is_dry_run; then
  run_sudo systemctl restart "$UNIT_NAME"
  info "[dry-run] 跳过 collector 稳定窗口检查"
else
  if ! run_sudo systemctl restart "$UNIT_NAME"; then
    error "collector 重启命令失败"
    rollback_collector "新版本 $VERSION 重启失败" || true
    exit 1
  fi
  if ! collector_stable_check "$UNIT_NAME" "$HEALTH_WAIT" "$RESTART_SINCE"; then
    if rollback_collector "新版本 $VERSION 稳定窗口检查失败"; then
      error "新版本 $VERSION 健康检查失败，已回滚到上一版本"
    else
      error "新版本 $VERSION 健康检查失败，且回滚失败，请人工检查"
    fi
    exit 1
  fi
fi

# ---------- 5. 清理旧版本 ----------
prune_releases "$COLLECTOR_DIR/releases" "$KEEP" "$VERSION"

info "collector 部署完成：$VERSION"
