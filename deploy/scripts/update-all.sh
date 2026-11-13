#!/usr/bin/env bash
# deploy/scripts/update-all.sh —— 板端按顺序更新 backend → frontend → collector
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
update-all.sh —— 板端按顺序更新多个组件

不带任何参数时只显示本帮助并退出 0。
执行必须显式指定 --all 或至少一个组件名（backend / frontend / collector）。

用法：
  bash deploy/scripts/update-all.sh --all --jar J --dist D --src S [选项]
  bash deploy/scripts/update-all.sh backend collector --jar J --src S [选项]

组件（固定执行顺序：backend → frontend → collector）：
  backend               调用 deploy-backend.sh，需要 --jar
  frontend              调用 deploy-frontend.sh，需要 --dist
  collector             调用 deploy-collector.sh，需要 --src

选择：
  --all                 更新全部三个组件（必须显式给出）
  位置参数组件名        仅更新列出的组件；可与 --all 同时使用
  --skip-backend        即使被选中也跳过 backend
  --skip-frontend       即使被选中也跳过 frontend
  --skip-collector      即使被选中也跳过 collector

来源参数（被选中的组件必需）：
  --jar <路径>          backend 的 yudao-server.jar
  --dist <目录>         frontend 的构建产物目录（含 index.html）
  --src <目录>          collector 源码目录（含 main.py）

选项：
  --version-prefix V    所有组件统一使用版本号 V（不再分别生成），
                        便于同批部署与回滚对应
  --keep N              每个组件 releases 保留版本数（默认 3，至少 1）
  --no-healthcheck      传给 deploy-backend.sh，跳过 48080 检查
  --dry-run             只打印动作，不复制、不切换、不重启
  -h, --help            显示本帮助并退出 0

行为：
  - 按固定顺序依次调用对应的板端脚本；
  - 任一组件失败立即停止，后续组件不再执行，退出码非 0；
  - backend 部署成功后默认等待 48080（约 140 秒启动），因此整体耗时较长。

前置条件：
  - 已执行 bootstrap-board.sh --run；
  - 产物已上传到板端本地（release.sh 会自动上传到 /tmp）。

示例：
  bash deploy/scripts/update-all.sh --all \
      --jar /tmp/yudao-server.jar \
      --dist /tmp/yudao-web-20261113-2200 \
      --src /tmp/yudao-collector-20261113-2200
  bash deploy/scripts/update-all.sh frontend collector \
      --dist /tmp/web --src /tmp/collector --version-prefix 20261113-2200
  bash deploy/scripts/update-all.sh --all --jar /tmp/x.jar --dry-run
EOF
}

if [ "$#" -eq 0 ]; then
  usage
  exit 0
fi

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac

ALL=0
SELECTED=()
SKIP_BACKEND=0
SKIP_FRONTEND=0
SKIP_COLLECTOR=0
VERSION_FIXED=""
KEEP=3
NO_HEALTHCHECK=0
JAR=""
DIST_DIR=""
SRC_DIR=""

selected_contains() {
  local comp="$1" existing
  for existing in ${SELECTED[@]+"${SELECTED[@]}"}; do
    [ "$existing" = "$comp" ] && return 0
  done
  return 1
}

add_component() {
  selected_contains "$1" || SELECTED+=("$1")
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --all)
      ALL=1
      shift
      ;;
    --skip-backend)
      SKIP_BACKEND=1
      shift
      ;;
    --skip-frontend)
      SKIP_FRONTEND=1
      shift
      ;;
    --skip-collector)
      SKIP_COLLECTOR=1
      shift
      ;;
    --version-prefix)
      [ "$#" -ge 2 ] || { usage >&2; die "--version-prefix 缺少参数"; }
      VERSION_FIXED="$2"
      shift 2
      ;;
    --keep)
      [ "$#" -ge 2 ] || { usage >&2; die "--keep 缺少参数"; }
      KEEP="$2"
      shift 2
      ;;
    --no-healthcheck)
      NO_HEALTHCHECK=1
      shift
      ;;
    --jar)
      [ "$#" -ge 2 ] || { usage >&2; die "--jar 缺少参数"; }
      JAR="$2"
      shift 2
      ;;
    --dist)
      [ "$#" -ge 2 ] || { usage >&2; die "--dist 缺少参数"; }
      DIST_DIR="$2"
      shift 2
      ;;
    --src)
      [ "$#" -ge 2 ] || { usage >&2; die "--src 缺少参数"; }
      SRC_DIR="$2"
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
    backend|frontend|collector)
      add_component "$1"
      shift
      ;;
    *)
      usage >&2
      die "未知参数或组件：$1"
      ;;
  esac
done

case "$KEEP" in
  ""|*[!0-9]*) usage >&2; die "--keep 必须是正整数：$KEEP" ;;
esac
[ "$KEEP" -ge 1 ] || { usage >&2; die "--keep 至少为 1"; }

if [ -n "$VERSION_FIXED" ]; then
  valid_version "$VERSION_FIXED" || { usage >&2; die "非法版本号：$VERSION_FIXED"; }
fi

if [ "$ALL" -eq 1 ]; then
  add_component backend
  add_component frontend
  add_component collector
fi

if [ "${#SELECTED[@]}" -eq 0 ]; then
  usage >&2
  die "必须指定 --all 或至少一个组件（backend / frontend / collector）"
fi

[ "$SKIP_BACKEND" -eq 1 ] && warn "跳过 backend"
[ "$SKIP_FRONTEND" -eq 1 ] && warn "跳过 frontend"
[ "$SKIP_COLLECTOR" -eq 1 ] && warn "跳过 collector"

export DRY_RUN
if [ -n "$VERSION_FIXED" ]; then
  info "统一版本号：$VERSION_FIXED"
fi
info "保留版本数：$KEEP"

run_component() {
  local comp="$1"
  local script="$SCRIPT_DIR/deploy-$comp.sh"
  local -a args=()

  [ -f "$script" ] || die "缺少板端脚本：$script"

  case "$comp" in
    backend)
      [ -n "$JAR" ] || { usage >&2; die "更新 backend 需要 --jar <路径>"; }
      args=(--jar "$JAR")
      [ "$NO_HEALTHCHECK" -eq 1 ] && args+=(--no-healthcheck)
      ;;
    frontend)
      [ -n "$DIST_DIR" ] || { usage >&2; die "更新 frontend 需要 --dist <目录>"; }
      args=(--dist "$DIST_DIR")
      ;;
    collector)
      [ -n "$SRC_DIR" ] || { usage >&2; die "更新 collector 需要 --src <目录>"; }
      args=(--src "$SRC_DIR")
      ;;
    *)
      die "未知组件：$comp"
      ;;
  esac

  args+=(--keep "$KEEP")
  [ -n "$VERSION_FIXED" ] && args+=(--version "$VERSION_FIXED")
  is_dry_run && args+=(--dry-run)

  info "==> 更新 $comp"
  if ! bash "$script" "${args[@]}"; then
    error "组件 $comp 更新失败，已停止后续组件"
    exit 1
  fi
  info "==> $comp 完成"
}

if [ "$SKIP_BACKEND" -eq 0 ] && selected_contains backend; then
  run_component backend
fi
if [ "$SKIP_FRONTEND" -eq 0 ] && selected_contains frontend; then
  run_component frontend
fi
if [ "$SKIP_COLLECTOR" -eq 0 ] && selected_contains collector; then
  run_component collector
fi

info "全部选定组件更新完成"
