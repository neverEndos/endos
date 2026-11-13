#!/usr/bin/env bash
# deploy/scripts/bootstrap-board.sh —— 板端新板初始化（幂等）
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

BASE="${YUDAO_BASE:-/opt/yudao}"
BACKEND_DIR="$BASE/backend"
WEB_DIR="$BASE/web"
COLLECTOR_DIR="$BASE/collector"
LOG_DIR="${YUDAO_LOG_DIR:-/var/log/yudao}"
ENV_DIR="${YUDAO_ENV_DIR:-/etc/yudao}"
ENV_FILE="${YUDAO_ENV_FILE:-$ENV_DIR/env.remote}"
NGINX_CONF="/etc/nginx/conf.d/collector.conf"
SYSTEMD_DIR="/etc/systemd/system"

usage() {
  cat <<'EOF'
bootstrap-board.sh —— 新板初始化（幂等，在板端以 sudo 执行）

不带任何参数时只显示本帮助并退出 0；执行必须显式指定 --run。

用法：
  sudo bash deploy/scripts/bootstrap-board.sh --run [选项]
  bash deploy/scripts/bootstrap-board.sh --run --dry-run

必须：
  --run                 确认执行。缺省时只显示帮助，不执行任何动作。

选项：
  --dry-run             只打印将要执行的动作，不改动系统。
  --skip-packages       跳过 apt 包检查与安装（适用于已装好依赖的板子）。
  --env-file <路径>     把该文件安装为 /etc/yudao/env.remote（0600 root）。
                        缺省时：目标已存在则保持不变；不存在则安装
                        deploy/env.example 模板并提示后续手工填写。
  --keep-releases N     清理各组件 releases 目录，仅保留最近 N 个版本（默认 3）。
                        只删除 releases 下的版本目录，绝不删除 current 软链接。
  -h, --help            显示本帮助并退出 0。

作用与副作用：
  - 检查/安装 apt 包：openjdk-25-jre-headless、nginx、
    postgresql-client、python3-psycopg2。
  - 创建目录：/opt/yudao/{backend,web,collector}/releases、
    /var/log/yudao（alientek 所有）、/etc/yudao（root 所有）。
  - 安装 systemd 单元 yudao-server.service、collector.service。
    kiosk.service 不自动安装（依赖图形会话，按需手工启用）。
  - 安装 /etc/nginx/conf.d/collector.conf，禁用 Nginx 默认站点。
  - enable nginx / yudao-server / collector；产物存在时才启动，
    缺少 jar/dist/main.py 时只 enable 并提示，不会启动空服务。
  - 检查时区、/dev/ttyS4 与 alientek 的 dialout 组（只告警，不擅自修改）。
  - 已存在的版本化布局、软链接、环境变量文件不会被破坏。

前置条件：
  - Ubuntu 24.04，当前用户可 sudo；若用 release.sh 远程调用会自动 sudo。
  - deploy/ 目录（含 systemd/、nginx/、env.example）已上传到板端。

示例：
  sudo bash deploy/scripts/bootstrap-board.sh --run
  sudo bash deploy/scripts/bootstrap-board.sh --run --skip-packages
  sudo bash deploy/scripts/bootstrap-board.sh --run \
      --env-file /tmp/env.remote --keep-releases 3
  bash deploy/scripts/bootstrap-board.sh --run --dry-run     # 只演练

相关脚本：
  deploy-backend.sh / deploy-frontend.sh / deploy-collector.sh / update-all.sh
EOF
}

RUN=0
SKIP_PACKAGES=0
ENV_SRC=""
KEEP_RELEASES=3

if [ "$#" -eq 0 ]; then
  usage
  exit 0
fi

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac

while [ "$#" -gt 0 ]; do
  case "$1" in
    --run)
      RUN=1
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --skip-packages)
      SKIP_PACKAGES=1
      shift
      ;;
    --env-file)
      [ "$#" -ge 2 ] || { usage >&2; die "--env-file 缺少参数"; }
      ENV_SRC="$2"
      shift 2
      ;;
    --keep-releases)
      [ "$#" -ge 2 ] || { usage >&2; die "--keep-releases 缺少参数"; }
      KEEP_RELEASES="$2"
      case "$KEEP_RELEASES" in
        ""|*[!0-9]*) usage >&2; die "--keep-releases 必须是正整数：$KEEP_RELEASES" ;;
      esac
      [ "$KEEP_RELEASES" -ge 1 ] || { usage >&2; die "--keep-releases 至少为 1"; }
      shift 2
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

[ "$RUN" -eq 1 ] || { usage >&2; die "bootstrap-board.sh 必须显式指定 --run 才会执行"; }
[ -n "$ENV_SRC" ] && [ ! -f "$ENV_SRC" ] && die "--env-file 文件不存在：$ENV_SRC"
export DRY_RUN

info "开始板端初始化（dry-run=$DRY_RUN）"
info "部署根目录：$BASE"
info "服务用户：$SERVICE_USER"

if [ "$(id -u)" -ne 0 ] && ! is_dry_run; then
  info "当前不是 root，需要 sudo 的动作将通过 sudo 执行"
fi

# 服务用户必须存在（systemd 单元与目录属主都使用它）
id -u "$SERVICE_USER" >/dev/null 2>&1 || die "系统用户不存在：$SERVICE_USER"

# ---------- 1. apt 包 ----------
PKGS=(openjdk-25-jre-headless nginx postgresql-client python3-psycopg2)
pkg_installed() {
  dpkg-query -W -f='${Status}' "$1" 2>/dev/null | grep -q 'install ok installed'
}

MISSING=()
for pkg in "${PKGS[@]}"; do
  if pkg_installed "$pkg"; then
    info "已安装：$pkg"
  else
    MISSING+=("$pkg")
  fi
done

if [ "$SKIP_PACKAGES" -eq 1 ]; then
  info "已指定 --skip-packages，跳过 apt 包检查/安装"
elif [ "${#MISSING[@]}" -eq 0 ]; then
  info "apt 依赖已齐全"
else
  info "缺少 apt 包：${MISSING[*]}"
  if is_dry_run; then
    printf '[dry-run]'
    printf ' %q' sudo apt-get update
    printf '\n'
    printf '[dry-run]'
    printf ' %q' sudo apt-get install -y "${MISSING[@]}"
    printf '\n'
  else
    run_sudo apt-get update
    run_sudo apt-get install -y "${MISSING[@]}"
    for pkg in "${MISSING[@]}"; do
      pkg_installed "$pkg" || warn "安装后仍未检测到：$pkg"
    done
  fi
fi

if command -v java >/dev/null 2>&1; then
  JAVA_VER="$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
  case "$JAVA_VER" in
    25*) info "Java 版本：$JAVA_VER" ;;
    *) warn "当前 java 版本为 ${JAVA_VER:-未知}，systemd 单元期望 JDK 25" ;;
  esac
elif [ "$SKIP_PACKAGES" -eq 0 ]; then
  warn "未检测到 java 命令"
fi

# ---------- 2. 目录 ----------
ensure_dir() {
  local dir="$1" owner="$2" group="$3" mode="$4"
  if [ -d "$dir" ]; then
    info "已存在：$dir"
  else
    info "创建目录：$dir（$owner:$group $mode）"
    run_sudo install -d -o "$owner" -g "$group" -m "$mode" "$dir"
  fi
}

for dir in "$BASE" "$BACKEND_DIR" "$BACKEND_DIR/releases" \
           "$WEB_DIR" "$WEB_DIR/releases" \
           "$COLLECTOR_DIR" "$COLLECTOR_DIR/releases"; do
  ensure_dir "$dir" "$SERVICE_USER" "$SERVICE_GROUP" 0755
done
ensure_dir "$LOG_DIR" "$SERVICE_USER" "$SERVICE_GROUP" 0750
ensure_dir "$ENV_DIR" root root 0755

# ---------- 3. systemd 单元 ----------
DAEMON_RELOAD=0
install_unit() {
  local name="$1" src="$DEPLOY_DIR/systemd/$1" dst="$SYSTEMD_DIR/$1"
  if [ ! -f "$src" ]; then
    warn "缺少 systemd 模板：$src（跳过 $name）"
    return 0
  fi
  if [ -f "$dst" ] && cmp -s "$src" "$dst"; then
    info "systemd 单元已是最新：$dst"
    return 0
  fi
  info "安装 systemd 单元：$dst"
  run_sudo install -m 0644 "$src" "$dst"
  DAEMON_RELOAD=1
}

install_unit yudao-server.service
install_unit collector.service

if [ "$DAEMON_RELOAD" -eq 1 ]; then
  info "systemd 单元有变化，执行 daemon-reload"
  run_sudo systemctl daemon-reload
fi

# ---------- 4. Nginx ----------
if ! command -v nginx >/dev/null 2>&1 && [ "$SKIP_PACKAGES" -eq 1 ]; then
  warn "未找到 nginx，跳过 Nginx 配置安装"
else
  NGINX_SRC="$DEPLOY_DIR/nginx/collector.conf"
  if [ -f "$NGINX_SRC" ]; then
    if [ -f "$NGINX_CONF" ] && cmp -s "$NGINX_SRC" "$NGINX_CONF"; then
      info "Nginx 站点配置已是最新：$NGINX_CONF"
    else
      info "安装 Nginx 站点配置：$NGINX_CONF"
      run_sudo install -m 0644 "$NGINX_SRC" "$NGINX_CONF"
    fi
  else
    warn "缺少 Nginx 模板：$NGINX_SRC（跳过）"
  fi

  if [ -e /etc/nginx/sites-enabled/default ] || [ -L /etc/nginx/sites-enabled/default ]; then
    info "禁用 Nginx 默认站点：/etc/nginx/sites-enabled/default"
    run_sudo rm -f /etc/nginx/sites-enabled/default
  else
    info "Nginx 默认站点已禁用"
  fi

  if command -v nginx >/dev/null 2>&1; then
    run_sudo nginx -t
    if service_active nginx; then
      info "Nginx 已在运行，重载配置"
      run_sudo systemctl reload nginx
    else
      info "启动并 enable Nginx"
      run_sudo systemctl enable --now nginx
    fi
    run_sudo systemctl enable nginx
  elif [ "$SKIP_PACKAGES" -eq 0 ]; then
    warn "未找到 nginx 命令，跳过 nginx -t 与启动"
  fi
fi

# ---------- 5. 环境变量文件 ----------
if [ -n "$ENV_SRC" ]; then
  info "安装环境变量文件：$ENV_SRC -> $ENV_FILE（0600 root）"
  run_sudo install -o root -g root -m 0600 "$ENV_SRC" "$ENV_FILE"
elif [ -f "$ENV_FILE" ]; then
  info "环境变量文件已存在，保持不变：$ENV_FILE"
elif [ -f "$DEPLOY_DIR/env.example" ]; then
  warn "未提供 --env-file，安装 env.example 占位模板：$ENV_FILE"
  warn "请立即执行 sudoedit $ENV_FILE 填写真实数据库/Redis 配置"
  run_sudo install -o root -g root -m 0600 "$DEPLOY_DIR/env.example" "$ENV_FILE"
else
  warn "未找到 $ENV_FILE，也没有 deploy/env.example 模板；服务可能无法连接数据库"
fi

# ---------- 6. 时区 / 串口检查（只告警） ----------
if command -v timedatectl >/dev/null 2>&1; then
  TZ_VALUE="$(timedatectl show -p Timezone --value 2>/dev/null || true)"
  if [ "$TZ_VALUE" = "Asia/Shanghai" ]; then
    info "时区：$TZ_VALUE"
  else
    warn "当前时区为 ${TZ_VALUE:-未知}，建议：sudo timedatectl set-timezone Asia/Shanghai"
  fi
  NTP_SYNCED="$(timedatectl show -p NTPSynchronized --value 2>/dev/null || true)"
  [ "$NTP_SYNCED" = "yes" ] || warn "NTP 尚未同步，请确认网络与时间同步服务"
else
  warn "未找到 timedatectl，无法检查时区"
fi

if [ -e /dev/ttyS4 ]; then
  info "串口存在：/dev/ttyS4"
else
  warn "未找到 /dev/ttyS4；collector 启动会失败，请核对串口设备名"
fi

if id -nG "$SERVICE_USER" 2>/dev/null | tr ' ' '\n' | grep -qx dialout; then
  info "用户 $SERVICE_USER 已属于 dialout 组"
else
  warn "用户 $SERVICE_USER 不在 dialout 组，串口可能无权限；"
  warn "修复：sudo usermod -aG dialout $SERVICE_USER（之后需重新登录或重启服务）"
fi

# ---------- 7. enable 服务（有产物才启动） ----------
ensure_service() {
  local unit="$1" artifact="$2" artifact_desc="$3"
  if [ -e "$artifact" ]; then
    if service_active "$unit"; then
      info "$unit 已在运行，仅确认 enable"
      run_sudo systemctl enable "$unit"
    else
      info "检测到产物（$artifact_desc），启动并 enable $unit"
      run_sudo systemctl enable --now "$unit"
    fi
  else
    info "未检测到产物（$artifact_desc），仅 enable $unit，待部署后再启动"
    run_sudo systemctl enable "$unit"
  fi
}

ensure_service yudao-server "$BACKEND_DIR/yudao-server.jar" "后端 jar"
ensure_service collector "$COLLECTOR_DIR/current/main.py" "collector current/main.py"

# ---------- 8. 清理旧版本（保留 current） ----------
BACKEND_CUR=""
BACKEND_TARGET="$(symlink_target_abs "$BACKEND_DIR/yudao-server.jar" 2>/dev/null || true)"
if [ -n "$BACKEND_TARGET" ]; then
  BACKEND_CUR="$(basename "$(dirname "$BACKEND_TARGET")")"
fi
WEB_CUR=""
WEB_TARGET="$(symlink_target_abs "$WEB_DIR/current" 2>/dev/null || true)"
if [ -n "$WEB_TARGET" ]; then
  WEB_CUR="$(basename "$WEB_TARGET")"
fi
COLLECTOR_CUR=""
COLLECTOR_TARGET="$(symlink_target_abs "$COLLECTOR_DIR/current" 2>/dev/null || true)"
if [ -n "$COLLECTOR_TARGET" ]; then
  COLLECTOR_CUR="$(basename "$COLLECTOR_TARGET")"
fi

info "清理旧版本（每个组件保留最近 $KEEP_RELEASES 个）"
prune_releases "$BACKEND_DIR/releases" "$KEEP_RELEASES" "$BACKEND_CUR"
prune_releases "$WEB_DIR/releases" "$KEEP_RELEASES" "$WEB_CUR"
prune_releases "$COLLECTOR_DIR/releases" "$KEEP_RELEASES" "$COLLECTOR_CUR"

info "板端初始化完成（dry-run=$DRY_RUN）"
if ! is_dry_run; then
  info "建议检查：systemctl status nginx yudao-server collector --no-pager"
fi
