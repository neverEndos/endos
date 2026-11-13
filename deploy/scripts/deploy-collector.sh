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
  --dry-run            只打印动作，不复制、不切软链接、不重启
  -h, --help           显示本帮助并退出 0

部署流程与副作用：
  1. 复制 src 内容到 /opt/yudao/collector/releases/<版本>/；
  2. 切换软链接 /opt/yudao/collector/current 指向新版本；
  3. 若 deploy/systemd/collector.service 与已装单元不同，安装并 daemon-reload；
  4. sudo systemctl restart collector，等待服务 active（默认最长 30 秒）；
  5. 启动失败时自动切回上一版本并重启，然后以非 0 退出；
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

# 记录上一版本，供启动失败时回滚
PREV_TARGET=""
if [ -L "$LINK" ]; then
  PREV_TARGET="$(symlink_target_abs "$LINK" 2>/dev/null || true)"
  [ -n "$PREV_TARGET" ] && [ -e "$PREV_TARGET" ] || PREV_TARGET=""
fi

# ---------- 1. 复制到版本目录 ----------
if [ -d "$TARGET_DIR" ]; then
  warn "版本目录已存在，将覆盖：$TARGET_DIR"
fi
if is_dry_run; then
  printf '[dry-run]'
  printf ' %q' rm -rf "$TARGET_DIR"
  printf '\n'
  printf '[dry-run]'
  printf ' %q' install -d "$TARGET_DIR"
  printf '\n'
  printf '[dry-run]'
  printf ' %q' cp -a "$SRC/." "$TARGET_DIR/"
  printf '\n'
else
  rm -rf "$TARGET_DIR"
  install -d "$TARGET_DIR"
  cp -a "$SRC/." "$TARGET_DIR/"
  # 清掉本机运行时产生的字节码缓存，避免带入版本目录
  find "$TARGET_DIR" -type d -name __pycache__ -prune -exec rm -rf {} + 2>/dev/null || true
  fix_owner "$TARGET_DIR"
  [ -f "$TARGET_DIR/main.py" ] || die "复制后缺少 main.py，部署中止"
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

# ---------- 4. 重启并检查 ----------
info "重启 collector（会先停止旧进程，释放 /dev/ttyS4）"
run_sudo systemctl restart "$UNIT_NAME"

if is_dry_run; then
  info "[dry-run] 跳过等待 collector 进入 active"
else
  info "等待 collector 进入 active（最长 30 秒）"
  if ! wait_service_active "$UNIT_NAME" 30; then
    error "collector 启动失败：服务未进入 active"
    if [ -n "$PREV_TARGET" ]; then
      warn "自动回滚到上一版本：$PREV_TARGET"
      switch_symlink "$PREV_TARGET" "$LINK"
      run_sudo systemctl restart "$UNIT_NAME"
      if wait_service_active "$UNIT_NAME" 30; then
        error "新版本 $VERSION 启动失败，已回滚到上一版本"
      else
        error "新版本 $VERSION 启动失败，且回滚后仍未 active，请人工检查"
      fi
    else
      error "没有可回滚的上一版本，请检查 journalctl -u collector"
    fi
    exit 1
  fi
  info "collector 健康检查通过：服务 active"
fi

# ---------- 5. 清理旧版本 ----------
prune_releases "$COLLECTOR_DIR/releases" "$KEEP" "$VERSION"

info "collector 部署完成：$VERSION"
