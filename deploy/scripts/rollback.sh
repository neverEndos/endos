#!/usr/bin/env bash
# deploy/scripts/rollback.sh —— 板端按组件回滚到上一版本或指定版本
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

BASE="${YUDAO_BASE:-/opt/yudao}"
BACKEND_DIR="$BASE/backend"
WEB_DIR="$BASE/web"
COLLECTOR_DIR="$BASE/collector"

usage() {
  cat <<'EOF'
rollback.sh —— 板端按组件回滚

不带任何参数时只显示本帮助并退出 0。

用法：
  bash deploy/scripts/rollback.sh <组件> [选项]

组件（位置参数，必需，只能选一个）：
  backend               回滚 /opt/yudao/backend/yudao-server.jar 软链接
  frontend              回滚 /opt/yudao/web/current 软链接
  collector             回滚 /opt/yudao/collector/current 软链接

选项：
  --version V           回滚到指定版本；默认取严格小于当前版本的最近版本
  --health-wait <秒>    collector 稳定窗口（默认 30 秒）；期间服务须保持 active，
                        且 journal 不得出现 PollError/轮询器无法启动/Traceback
  --dry-run             只打印将切换的版本与重启动作，不实际切换
  -h, --help            显示本帮助并退出 0

行为：
  - backend：切换 jar 软链接，sudo systemctl restart yudao-server，
    等待 127.0.0.1:48080（最长 240 秒）；
  - frontend：切换 current 软链接，sudo nginx -t + reload，
    请求 http://127.0.0.1/ 要求 200（最长 30 秒）；
  - collector：切换 current 软链接，sudo systemctl restart collector，
    执行 --health-wait 秒稳定窗口检查（服务 active、journal 无启动错误）。
  - 健康检查失败时以非 0 退出，不会自动继续尝试更早版本。

前置条件：
  - 目标版本目录仍在 releases/ 下（部署脚本默认保留最近 3 个版本）；
  - 以 alientek 或 root 执行；systemctl/nginx 需要 sudo 权限。

示例：
  bash deploy/scripts/rollback.sh frontend
  bash deploy/scripts/rollback.sh backend --version 20261113-1842-d785623e
  bash deploy/scripts/rollback.sh collector --dry-run
  bash deploy/scripts/rollback.sh collector --health-wait 30

说明：
  - “上一版本”取 releases 中**严格小于当前版本**的最大版本名；即使存在比当前
    更新的版本（例如 V3）也会跳过；没有上一版本时直接报错退出；
  - 数据库结构若与版本绑定，回滚后端前请先评估数据兼容性。
EOF
}

if [ "$#" -eq 0 ]; then
  usage
  exit 0
fi

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac

COMP=""
TARGET_VERSION=""
HEALTH_WAIT=30

while [ "$#" -gt 0 ]; do
  case "$1" in
    backend|frontend|collector)
      [ -z "$COMP" ] || { usage >&2; die "只能指定一个组件（已指定：$COMP）"; }
      COMP="$1"
      shift
      ;;
    --version)
      [ "$#" -ge 2 ] || { usage >&2; die "--version 缺少参数"; }
      TARGET_VERSION="$2"
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
      die "未知参数或组件：$1"
      ;;
  esac
done

[ -n "$COMP" ] || { usage >&2; die "缺少组件参数（backend / frontend / collector）"; }
if [ -n "$TARGET_VERSION" ]; then
  valid_version "$TARGET_VERSION" || { usage >&2; die "非法版本号：$TARGET_VERSION"; }
fi
case "$HEALTH_WAIT" in
  ""|*[!0-9]*) usage >&2; die "--health-wait 必须是正整数：$HEALTH_WAIT" ;;
esac
[ "$HEALTH_WAIT" -ge 1 ] || { usage >&2; die "--health-wait 至少为 1"; }

export DRY_RUN

case "$COMP" in
  backend)
    LINK="$BACKEND_DIR/yudao-server.jar"
    RELEASES="$BACKEND_DIR/releases"
    ;;
  frontend)
    LINK="$WEB_DIR/current"
    RELEASES="$WEB_DIR/releases"
    ;;
  collector)
    LINK="$COLLECTOR_DIR/current"
    RELEASES="$COLLECTOR_DIR/releases"
    ;;
esac

[ -d "$RELEASES" ] || die "缺少 releases 目录：$RELEASES"
[ -L "$LINK" ] || die "当前软链接不存在：$LINK"

CUR_TARGET="$(symlink_target_abs "$LINK" 2>/dev/null || true)"
[ -n "$CUR_TARGET" ] || die "无法解析当前软链接：$LINK"
if [ "$COMP" = "backend" ]; then
  CUR_VERSION="$(basename "$(dirname "$CUR_TARGET")")"
else
  CUR_VERSION="$(basename "$CUR_TARGET")"
fi
info "当前 $COMP 版本：$CUR_VERSION"

if [ -z "$TARGET_VERSION" ]; then
  if ! TARGET_VERSION="$(previous_release "$RELEASES" "$CUR_VERSION")"; then
    die "没有可回滚的上一版本：$RELEASES 中不存在严格小于 $CUR_VERSION 的版本"
  fi
fi

[ "$TARGET_VERSION" != "$CUR_VERSION" ] || die "目标版本与当前版本相同：$TARGET_VERSION"

case "$COMP" in
  backend)
    [ -f "$RELEASES/$TARGET_VERSION/yudao-server.jar" ] || die "目标版本缺少 yudao-server.jar：$RELEASES/$TARGET_VERSION"
    SWITCH_TARGET="releases/$TARGET_VERSION/yudao-server.jar"
    ;;
  frontend)
    [ -f "$RELEASES/$TARGET_VERSION/index.html" ] || die "目标版本缺少 index.html：$RELEASES/$TARGET_VERSION"
    SWITCH_TARGET="releases/$TARGET_VERSION"
    ;;
  collector)
    [ -f "$RELEASES/$TARGET_VERSION/main.py" ] || die "目标版本缺少 main.py：$RELEASES/$TARGET_VERSION"
    SWITCH_TARGET="releases/$TARGET_VERSION"
    ;;
esac

info "计划回滚 $COMP：$CUR_VERSION -> $TARGET_VERSION"
info "切换软链接：$LINK -> $SWITCH_TARGET"

# 回滚是“切软链接 + 重启/重载”；切换使用临时软链接 + mv -Tf 原子替换。
switch_symlink "$SWITCH_TARGET" "$LINK"

case "$COMP" in
  backend)
    info "重启 yudao-server"
    run_sudo systemctl restart yudao-server
    if is_dry_run; then
      info "[dry-run] 跳过等待 127.0.0.1:48080（最长 240 秒）"
    else
      info "等待后端监听 127.0.0.1:48080（最长 240 秒）"
      wait_port 127.0.0.1 48080 240 || die "回滚后 48080 未在 240 秒内监听"
      info "后端回滚健康检查通过"
    fi
    ;;
  frontend)
    info "校验并重载 Nginx"
    run_sudo nginx -t
    run_sudo systemctl reload nginx
    if is_dry_run; then
      info "[dry-run] 跳过请求 http://127.0.0.1/ 检查 200"
    else
      wait_http "http://127.0.0.1/" 30 200 || die "回滚后 http://127.0.0.1/ 未返回 200"
      info "前端回滚健康检查通过：http://127.0.0.1/ 返回 200"
    fi
    ;;
  collector)
    info "重启 collector（会先停止旧进程，释放 /dev/ttyS4）"
    ROLLBACK_SINCE="$(date '+%Y-%m-%d %H:%M:%S')"
    run_sudo systemctl restart collector
    if is_dry_run; then
      info "[dry-run] 跳过 collector 稳定窗口检查"
    else
      collector_stable_check collector "$HEALTH_WAIT" "$ROLLBACK_SINCE" \
        || die "回滚后 collector 稳定窗口检查失败"
      info "collector 回滚健康检查通过"
    fi
    ;;
esac

info "$COMP 回滚完成：$TARGET_VERSION（原版本 $CUR_VERSION 仍保留在 releases/）"
