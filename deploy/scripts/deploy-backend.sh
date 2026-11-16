#!/usr/bin/env bash
# deploy/scripts/deploy-backend.sh —— 板端后端 jar 版本化部署
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

BASE="${YUDAO_BASE:-/opt/yudao}"
BACKEND_DIR="$BASE/backend"
LINK="$BACKEND_DIR/yudao-server.jar"
ENV_FILE="${YUDAO_ENV_FILE:-${YUDAO_ENV_DIR:-/etc/yudao}/env.remote}"
UNIT_TEMPLATE="$DEPLOY_DIR/systemd/yudao-server.service"
UNIT_DST="/etc/systemd/system/yudao-server.service"
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
  --version V           版本号（默认 YYYYMMDD-HHMMSS-<jar sha256 前 8 位>）
                        取值只能包含字母、数字、点、下划线、连字符
  --keep N              releases 目录保留版本数（默认 3，至少 1）
  --no-healthcheck     重启后不等待 48080 端口（不推荐）
  --allow-mock         允许在 mock 可能开启时部署（fail-open，仅限离线演示）
  --env-file <路径>     mock 门禁读取的环境变量文件
                       （默认 $YUDAO_ENV_FILE 或 /etc/yudao/env.remote）
  --dry-run            只打印动作，不复制、不切软链接、不重启
  -h, --help           显示本帮助并退出 0

部署流程与副作用：
  0. mock 门禁：只有 YUDAO_SECURITY_MOCK_ENABLE=false/0/off/no 放行；
     缺失键、空值、true/1/yes/on 或未知值默认拒绝部署（--allow-mock 可覆盖）；
  1. 复制 jar 到 /opt/yudao/backend/releases/<版本>/yudao-server.jar；
  2. 切换软链接 /opt/yudao/backend/yudao-server.jar 指向新版本；
  3. 若随包提供新版 systemd/yudao-server.service，则安装并 daemon-reload；
  4. sudo systemctl restart yudao-server；
  5. 等待 127.0.0.1:48080 可连接（默认最长 240 秒），并校验 ss -lnt
     只出现回环地址（127.0.0.1:48080，允许 [::ffff:127.0.0.1]:48080）；
  6. 健康检查或监听校验失败时统一回滚到上一版本并重启，然后以非 0 退出；
  7. 成功后清理旧版本目录，保留最近 --keep 个。

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
ALLOW_MOCK=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --allow-mock)
      ALLOW_MOCK=1
      shift
      ;;
    --env-file)
      [ "$#" -ge 2 ] || { usage >&2; die "--env-file 缺少参数"; }
      ENV_FILE="$2"
      shift 2
      ;;
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

# mock 门禁必须在任何部署副作用之前执行：默认 fail-closed，拒绝部署并退出。
ALLOW_MOCK_ARG=()
[ "$ALLOW_MOCK" -eq 1 ] && ALLOW_MOCK_ARG=(--allow-mock)
if ! ensure_backend_mock_disabled "$ENV_FILE" ${ALLOW_MOCK_ARG[@]+"${ALLOW_MOCK_ARG[@]}"}; then
  error "已拒绝部署后端（mock 鉴权未确认关闭）；如确需离线演示请追加 --allow-mock"
  exit 1
fi

export DRY_RUN
TARGET_DIR="$BACKEND_DIR/releases/$VERSION"
TARGET_JAR="$TARGET_DIR/yudao-server.jar"

info "后端部署版本：$VERSION"
info "源 jar：$JAR"

# 记录当前（上一）版本，供健康检查失败时回滚；同版本重部署拒绝覆盖。
CUR_TARGET=""
CUR_VERSION=""
PREV_TARGET=""
if [ -L "$LINK" ]; then
  CUR_TARGET="$(symlink_target_abs "$LINK" 2>/dev/null || true)"
  if [ -n "$CUR_TARGET" ] && [ -e "$CUR_TARGET" ]; then
    PREV_TARGET="$CUR_TARGET"
    CUR_VERSION="$(basename "$(dirname "$CUR_TARGET")")"
  fi
fi
if [ "$VERSION" = "$CUR_VERSION" ]; then
  die "版本 $VERSION 已是当前版本，拒绝覆盖；请使用新的 --version 或先回滚"
fi

# ---------- 1. 复制到版本目录（临时文件 + 原子替换） ----------
if [ -d "$TARGET_DIR" ]; then
  warn "版本目录已存在（非当前版本），将替换：$TARGET_DIR"
fi
TMP_JAR="$TARGET_JAR.tmp.$$"
run install -d "$TARGET_DIR"
if is_dry_run; then
  run install -m 0644 "$JAR" "$TMP_JAR"
  run mv -Tf "$TMP_JAR" "$TARGET_JAR"
else
  install -m 0644 "$JAR" "$TMP_JAR"
  mv -Tf "$TMP_JAR" "$TARGET_JAR"
  fix_owner "$TARGET_DIR"
fi

# ---------- 2. 切换软链接 ----------
info "切换软链接：$LINK -> releases/$VERSION/yudao-server.jar"
switch_symlink "releases/$VERSION/yudao-server.jar" "$LINK"

# ---------- 2.5 刷新 systemd 单元 ----------
# 常规部署也会随包上传 deploy/systemd/；单元有变化时必须安装并 daemon-reload，
# 否则新的 --server.address=127.0.0.1 等启动参数无法落地。
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
  warn "未随包提供 systemd 单元模板（$UNIT_TEMPLATE），跳过单元刷新"
fi

rollback_backend() {
  local reason="$1"
  if [ -z "$PREV_TARGET" ]; then
    error "$reason；没有可回滚的上一版本，请人工检查 journalctl -u yudao-server"
    return 1
  fi
  warn "$reason，自动回滚到上一版本：$PREV_TARGET"
  switch_symlink "$PREV_TARGET" "$LINK"
  if ! run_sudo systemctl restart yudao-server; then
    error "回滚到上一版本后重启失败，请人工检查 journalctl -u yudao-server"
    return 1
  fi
  if wait_port 127.0.0.1 48080 240; then
    info "已回滚到上一版本：$PREV_TARGET"
    return 0
  fi
  error "回滚到上一版本后 48080 仍未监听"
  return 1
}

# ---------- 3. 重启 ----------
info "重启 yudao-server"
if ! is_dry_run && ! run_sudo systemctl restart yudao-server; then
  error "yudao-server 重启命令失败"
  rollback_backend "新版本 $VERSION 重启失败" || true
  exit 1
fi
if is_dry_run; then
  run_sudo systemctl restart yudao-server
fi

# ---------- 4. 健康检查 ----------
if [ "$HEALTHCHECK" -eq 1 ]; then
  if is_dry_run; then
    info "[dry-run] 跳过等待 127.0.0.1:48080（最长 ${HEALTH_TIMEOUT} 秒）"
  else
    info "等待后端监听 127.0.0.1:48080（最长 ${HEALTH_TIMEOUT} 秒）"
    if ! wait_port 127.0.0.1 48080 "$HEALTH_TIMEOUT"; then
      error "健康检查失败：48080 未在 ${HEALTH_TIMEOUT} 秒内监听"
      if rollback_backend "新版本 $VERSION 健康检查失败"; then
        error "新版本 $VERSION 健康检查失败，已回滚到上一版本"
      else
        error "新版本 $VERSION 健康检查失败，且回滚失败，请人工检查 journalctl -u yudao-server"
      fi
      exit 1
    fi
    # 端口可连接还不够：必须确认只监听回环地址，避免 0.0.0.0/*:48080 暴露到局域网。
    if ! check_backend_listen_loopback; then
      if rollback_backend "新版本 $VERSION 监听地址不符合回环要求"; then
        error "新版本 $VERSION 监听校验失败，已回滚到上一版本"
      else
        error "新版本 $VERSION 监听校验失败，且回滚失败，请人工检查 journalctl -u yudao-server"
      fi
      exit 1
    fi
    info "后端健康检查通过：48080 仅回环监听"
  fi
else
  warn "已指定 --no-healthcheck，跳过 48080 端口检查"
fi

# ---------- 5. 清理旧版本 ----------
prune_releases "$BACKEND_DIR/releases" "$KEEP" "$VERSION"

info "后端部署完成：$VERSION"
