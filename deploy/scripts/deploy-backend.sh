#!/usr/bin/env bash
# deploy/scripts/deploy-backend.sh —— 板端后端 jar 版本化部署
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

BASE="${YUDAO_BASE:-/opt/yudao}"
BACKEND_DIR="$BASE/backend"
LINK="$BACKEND_DIR/yudao-server.jar"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}"

usage() {
  cat <<'EOF'
deploy-backend.sh —— 板端后端 jar 版本化部署

不带任何参数时只显示本帮助并退出 0。

用法：
  bash deploy/scripts/deploy-backend.sh --jar <路径> [选项]

必须：
  --jar <路径>          新构建的 yudao-server.jar 路径（板端本地路径）
选项：
  --version V           版本号（默认 YYYYMMDD-HHMM-<jar sha256 前 8 位>）
                        取值只能包含字母、数字、点、下划线、连字符
  --keep N              releases 目录保留版本数（默认 3，至少 1）
  --no-healthcheck     重启后不等待 48080 端口（不推荐）
  --dry-run            只打印动作，不复制、不切软链接、不重启
  -h, --help           显示本帮助并退出 0

部署流程与副作用：
  1. 复制 jar 到 /opt/yudao/backend/releases/<版本>/yudao-server.jar；
  2. 切换软链接 /opt/yudao/backend/yudao-server.jar 指向新版本；
  3. sudo systemctl restart yudao-server；
  4. 等待 127.0.0.1:48080 可连接（默认最长 240 秒）；
  5. 健康检查失败时自动切回上一版本并重启，然后以非 0 退出；
  6. 成功后清理旧版本目录，保留最近 --keep 个。

前置条件：
  - 已执行 bootstrap-board.sh --run，目录与 systemd 单元就绪；
  - 以 alientek 或 root 执行；systemctl 需要 sudo 权限。

示例：
  bash deploy/scripts/deploy-backend.sh --jar /tmp/yudao-server.jar
  bash deploy/scripts/deploy-backend.sh --jar /tmp/yudao-server.jar \
      --version 20261113-2200-72ad2427 --keep 3
  bash deploy/scripts/deploy-backend.sh --jar /tmp/yudao-server.jar --dry-run

说明：
  - 后端首次启动约 140 秒后监听 48080，健康检查默认等待 240 秒。
  - 开发机通常通过 release.sh backend 调用本脚本。
EOF
}

if [ "$#" -eq 0 ]; then
  usage
  exit 0
fi

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac

JAR=""
VERSION=""
KEEP=3
HEALTHCHECK=1

while [ "$#" -gt 0 ]; do
  case "$1" in
    --jar)
      [ "$#" -ge 2 ] || { usage >&2; die "--jar 缺少参数"; }
      JAR="$2"
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
    --no-healthcheck)
      HEALTHCHECK=0
      shift
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

[ -n "$JAR" ] || { usage >&2; die "缺少必需参数 --jar"; }
case "$KEEP" in
  ""|*[!0-9]*) usage >&2; die "--keep 必须是正整数：$KEEP" ;;
esac
[ "$KEEP" -ge 1 ] || { usage >&2; die "--keep 至少为 1"; }

if [ ! -f "$JAR" ]; then
  # dry-run 时允许 artifact 不存在，便于开发机演练
  if is_dry_run; then
    warn "jar 不存在（dry-run 继续）：$JAR"
  else
    die "jar 不存在：$JAR"
  fi
fi

# 转成绝对路径（dry-run 且文件不存在时保留原值）
if [ -f "$JAR" ]; then
  JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"
fi

if [ -z "$VERSION" ]; then
  VERSION="$(new_version "$JAR")"
fi
valid_version "$VERSION" || { usage >&2; die "非法版本号：$VERSION"; }

[ -d "$BACKEND_DIR/releases" ] || die "缺少目录 $BACKEND_DIR/releases，请先运行 bootstrap-board.sh --run"

export DRY_RUN
TARGET_DIR="$BACKEND_DIR/releases/$VERSION"
TARGET_JAR="$TARGET_DIR/yudao-server.jar"

info "后端部署版本：$VERSION"
info "源 jar：$JAR"

# 记录上一版本，供健康检查失败时回滚
PREV_TARGET=""
if [ -L "$LINK" ]; then
  PREV_TARGET="$(symlink_target_abs "$LINK" 2>/dev/null || true)"
  [ -n "$PREV_TARGET" ] && [ -e "$PREV_TARGET" ] || PREV_TARGET=""
fi

# ---------- 1. 复制到版本目录 ----------
if [ -d "$TARGET_DIR" ]; then
  warn "版本目录已存在，将覆盖：$TARGET_DIR"
fi
run install -d "$TARGET_DIR"
run install -m 0644 "$JAR" "$TARGET_JAR"
if ! is_dry_run; then
  fix_owner "$TARGET_DIR"
fi

# ---------- 2. 切换软链接 ----------
info "切换软链接：$LINK -> releases/$VERSION/yudao-server.jar"
switch_symlink "releases/$VERSION/yudao-server.jar" "$LINK"

# ---------- 3. 重启 ----------
info "重启 yudao-server"
run_sudo systemctl restart yudao-server

# ---------- 4. 健康检查 ----------
if [ "$HEALTHCHECK" -eq 1 ]; then
  if is_dry_run; then
    info "[dry-run] 跳过等待 127.0.0.1:48080（最长 ${HEALTH_TIMEOUT} 秒）"
  else
    info "等待后端监听 127.0.0.1:48080（最长 ${HEALTH_TIMEOUT} 秒）"
    if ! wait_port 127.0.0.1 48080 "$HEALTH_TIMEOUT"; then
      error "健康检查失败：48080 未在 ${HEALTH_TIMEOUT} 秒内监听"
      if [ -n "$PREV_TARGET" ]; then
        warn "自动回滚到上一版本：$PREV_TARGET"
        switch_symlink "$PREV_TARGET" "$LINK"
        run_sudo systemctl restart yudao-server
        info "等待回滚后的后端启动（最长 240 秒）"
        if wait_port 127.0.0.1 48080 240; then
          error "新版本 $VERSION 健康检查失败，已回滚到上一版本"
        else
          error "新版本 $VERSION 健康检查失败，且回滚后仍未监听 48080，请人工检查"
        fi
      else
        error "没有可回滚的上一版本，请人工检查 journalctl -u yudao-server"
      fi
      exit 1
    fi
    info "后端健康检查通过：48080 可连接"
  fi
else
  warn "已指定 --no-healthcheck，跳过 48080 端口检查"
fi

# ---------- 5. 清理旧版本 ----------
prune_releases "$BACKEND_DIR/releases" "$KEEP" "$VERSION"

info "后端部署完成：$VERSION"
