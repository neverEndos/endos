#!/usr/bin/env bash
# deploy/release.sh —— 开发机入口：构建、上传并调用板端部署脚本
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/lib/common.sh
source "$SCRIPT_DIR/scripts/lib/common.sh"

DEPLOY_DIR="$SCRIPT_DIR"
REPO_ROOT="$(cd "$DEPLOY_DIR/.." && pwd)"
DEPLOY_ENV_FILE="$DEPLOY_DIR/deploy.env"

# 固定路径
REMOTE_STAGE="/tmp/yudao-deploy"
REMOTE_SCRIPTS="$REMOTE_STAGE/scripts"
FRONTEND_APP="$REPO_ROOT/yudao-ui/yudao-ui-admin-vben/apps/web-antdv-next"
BACKEND_JAR_LOCAL="$REPO_ROOT/yudao-server/target/yudao-server.jar"
FRONTEND_DIST_LOCAL="$FRONTEND_APP/dist"
COLLECTOR_LOCAL="$REPO_ROOT/collector"

usage() {
  cat <<'EOF'
release.sh —— 开发机部署入口（构建 → 上传 → 调用板端脚本）

不带任何参数时只显示本帮助并退出 0。
所有动作都可以先加 --dry-run 演练：只打印构建/上传/远端命令，不实际执行。

用法：
  ./deploy/release.sh <子命令> [选项]

子命令：
  bootstrap       上传 deploy/ 模板与脚本，调用 bootstrap-board.sh --run 初始化板端
  backend         构建后端 jar，上传并调用 deploy-backend.sh
  frontend        构建前端 dist，上传并调用 deploy-frontend.sh
  collector       打包 collector/，上传并调用 deploy-collector.sh
  all             依次执行 backend → frontend → collector（共用同一版本号）
  rollback        调用板端 rollback.sh，按组件回滚
  -h, --help      显示本帮助并退出 0

公共选项（可用于所有子命令）：
  --host <地址>          板端地址（默认 192.168.0.105）
  --user <用户>          板端 SSH 用户（默认 alientek）
  --port <端口>          SSH 端口（默认 22）
  --key <私钥路径>       本机 SSH 私钥（默认 ~/.ssh/id_rsa）
  --sudo-password <密码> 板端 sudo 密码，仅 dev 环境使用；缺省时用 ssh -t
                         交互输入。注意：写在命令行会进入 shell 历史/进程列表，
                         推荐写入被 gitignore 的 deploy/deploy.env。
  --dry-run              只打印动作，不构建、不上传、不执行远端命令
  -h, --help             显示本帮助并退出 0

backend 选项：
  --skip-build           跳过 mvn 构建，直接使用 yudao-server/target/yudao-server.jar
  --version V            指定版本号（默认自动生成 YYYYMMDD-HHMM-<git sha 前 8 位>）
  --keep N               releases 保留版本数（默认 3）
  --no-healthcheck       传给板端脚本，跳过 48080 健康检查

frontend 选项：
  --skip-build           跳过 pnpm build，直接使用 apps/web-antdv-next/dist
  --version V            指定版本号（默认自动生成）
  --keep N               releases 保留版本数（默认 3）

collector 选项：
  --version V            指定版本号（默认自动生成）
  --keep N               releases 保留版本数（默认 3）

all 选项：
  --skip-build           跳过后端与前端构建
  --skip-backend / --skip-frontend / --skip-collector
  --version V            三组件共用版本号（默认自动生成一次）
  --keep N               releases 保留版本数（默认 3）
  --no-healthcheck       传给后端部署脚本

rollback 参数/选项：
  <组件>                 backend | frontend | collector（必需位置参数）
  --version V            回滚到指定版本（默认板端当前版本的上一版本）

bootstrap 选项：
  --skip-packages        传给 bootstrap-board.sh，跳过 apt 安装
  --env-file <本地路径>  上传（0600）并安装为板端 /etc/yudao/env.remote；
                         安装后清理 /tmp 上传副本
  --allow-mock          允许 env 中 YUDAO_SECURITY_MOCK_ENABLE=true 时启动后端
                         （仅限离线演示；默认拒绝启动）
  --keep-releases N      板端各组件 releases 保留版本数（默认 3）

构建与上传细节：
  - backend：mvn -B clean package -pl yudao-server -am -Dmaven.test.skip=true，
    产物 yudao-server/target/yudao-server.jar，上传到 /tmp/yudao-server-<版本>.jar；
  - frontend：在 apps/web-antdv-next 执行 pnpm build，dist 以 tar 流上传解包到
    /tmp/yudao-web-<版本>；
  - collector：直接打包 collector/ 目录，上传解包到 /tmp/yudao-collector-<版本>；
  - 每个子命令都会把 deploy/scripts 上传到板端 /tmp/yudao-deploy/scripts 后调用；
  - deploy.env 不会随 deploy/ 上传（bootstrap 打包时已排除）。

配置来源：
  deploy/deploy.env（存在时读取，已被 deploy/.gitignore 忽略），CLI 参数优先。
  可配置项：BOARD_HOST、BOARD_USER、BOARD_PORT、SSH_KEY、BOARD_SUDO_PASSWORD。
  模板：deploy/deploy.env.example。

安全提示：
  - 不要提交 deploy.env；不要把真实密码写进仓库或脚本；
  - deploy.env 只作为当前 shell 变量读取（不使用 set -a 导出），
    BOARD_SUDO_PASSWORD 不会进入 mvn/pnpm 构建进程环境；
  - --env-file 上传后立即 chmod 600，bootstrap 安装结束（成功或失败）即删除
    /tmp/yudao-deploy/env.remote.upload；
  - 板端 sudo 密码只用于开发联调，生产环境应使用受限 sudoers 规则；
  - 远端脚本通过 sudo 执行，部署产物会自动 chown 回 alientek。

示例：
  cp deploy/deploy.env.example deploy/deploy.env   # 首次配置
  ./deploy/release.sh bootstrap
  ./deploy/release.sh backend
  ./deploy/release.sh frontend --skip-build
  ./deploy/release.sh collector
  ./deploy/release.sh all --dry-run
  ./deploy/release.sh rollback frontend
  ./deploy/release.sh rollback backend --version 20261113-1842-d785623e
EOF
}

die_usage() {
  usage >&2
  die "$*"
}

# ---------- 配置 ----------
if [ -f "$DEPLOY_ENV_FILE" ]; then
  # 只作为当前 shell 的变量读取，不用 set -a 导出：BOARD_SUDO_PASSWORD 等
  # 敏感值绝不能进入 mvn/pnpm 等子进程的环境。
  # shellcheck disable=SC1090
  . "$DEPLOY_ENV_FILE"
fi

BOARD_HOST="${BOARD_HOST:-192.168.0.105}"
BOARD_USER="${BOARD_USER:-alientek}"
BOARD_PORT="${BOARD_PORT:-22}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_rsa}"
BOARD_SUDO_PASSWORD="${BOARD_SUDO_PASSWORD:-}"

expand_tilde() {
  case "$1" in
    "~") printf '%s\n' "$HOME" ;;
    "~/"*) printf '%s/%s\n' "$HOME" "${1#\~/}" ;;
    *) printf '%s\n' "$1" ;;
  esac
}

sanitize_config() {
  BOARD_HOST="${BOARD_HOST%$'\r'}"
  BOARD_USER="${BOARD_USER%$'\r'}"
  BOARD_PORT="${BOARD_PORT%$'\r'}"
  SSH_KEY="${SSH_KEY%$'\r'}"
  BOARD_SUDO_PASSWORD="${BOARD_SUDO_PASSWORD%$'\r'}"
  SSH_KEY="$(expand_tilde "$SSH_KEY")"
}

# 解析公共选项，剩余参数放进 REMAIN，由子命令继续解析
REMAIN=()
parse_common_args() {
  REMAIN=()
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --host)
        [ "$#" -ge 2 ] || die_usage "--host 缺少参数"
        BOARD_HOST="$2"
        shift 2
        ;;
      --user)
        [ "$#" -ge 2 ] || die_usage "--user 缺少参数"
        BOARD_USER="$2"
        shift 2
        ;;
      --port)
        [ "$#" -ge 2 ] || die_usage "--port 缺少参数"
        BOARD_PORT="$2"
        shift 2
        ;;
      --key)
        [ "$#" -ge 2 ] || die_usage "--key 缺少参数"
        SSH_KEY="$2"
        shift 2
        ;;
      --sudo-password)
        [ "$#" -ge 2 ] || die_usage "--sudo-password 缺少参数"
        BOARD_SUDO_PASSWORD="$2"
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
        REMAIN+=("$1")
        shift
        ;;
    esac
  done
  sanitize_config
}

validate_config() {
  case "$BOARD_PORT" in
    ""|*[!0-9]*) die "SSH 端口非法：$BOARD_PORT" ;;
  esac
  [ "$BOARD_PORT" -ge 1 ] && [ "$BOARD_PORT" -le 65535 ] || die "SSH 端口越界：$BOARD_PORT"
  [ -n "$BOARD_HOST" ] || die "BOARD_HOST 不能为空"
  [ -n "$BOARD_USER" ] || die "BOARD_USER 不能为空"
  if is_dry_run; then
    info "[dry-run] 目标板：$BOARD_USER@$BOARD_HOST:$BOARD_PORT"
    return 0
  fi
  require_cmd ssh
  require_cmd scp
  require_cmd tar
  [ -f "$SSH_KEY" ] || die "SSH 私钥不存在：$SSH_KEY（可用 --key 指定）"
}

SSH_OPTS=()
SCP_OPTS=()
TARGET=""
build_remote_opts() {
  TARGET="$BOARD_USER@$BOARD_HOST"
  SSH_OPTS=(-o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -p "$BOARD_PORT")
  SCP_OPTS=(-o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -P "$BOARD_PORT")
  if [ -n "$SSH_KEY" ]; then
    SSH_OPTS+=(-i "$SSH_KEY")
    SCP_OPTS+=(-i "$SSH_KEY")
  fi
}

init_remote() {
  validate_config
  build_remote_opts
}

# ---------- 远端执行 ----------
remote_sudo_exec() {
  local desc="$1" cmd="$2"
  if is_dry_run; then
    info "[dry-run] $desc：ssh $TARGET 'sudo $cmd'"
    return 0
  fi
  info "$desc"
  if [ -n "$BOARD_SUDO_PASSWORD" ]; then
    # 密码经 ssh 加密通道送入远端 sudo 的标准输入，不出现在远端命令行
    printf '%s\n' "$BOARD_SUDO_PASSWORD" | \
      ssh "${SSH_OPTS[@]}" "$TARGET" "sudo -S -p '' $cmd"
  else
    if [ ! -t 0 ]; then
      die "未配置 BOARD_SUDO_PASSWORD 且当前不是交互终端，无法输入 sudo 密码；请在终端运行或写入 deploy/deploy.env"
    fi
    ssh -t "${SSH_OPTS[@]}" "$TARGET" "sudo $cmd"
  fi
}

# 调用板端脚本；dry-run 时自动追加 --dry-run
remote_bash_sudo() {
  local name="$1"
  shift
  local cmd="bash $REMOTE_SCRIPTS/$name" a
  local -a remote_args=("$@")
  is_dry_run && remote_args+=(--dry-run)
  for a in "${remote_args[@]}"; do
    cmd+=" $(printf '%q' "$a")"
  done
  remote_sudo_exec "调用板端 $name" "$cmd"
}

# ---------- 上传 ----------
upload_scripts() {
  local tgz size
  tgz="$(mktemp "${TMPDIR:-/tmp}/yudao-scripts.XXXXXX.tgz")"
  tar -C "$DEPLOY_DIR" -czf "$tgz" scripts
  size="$(du -h "$tgz" | cut -f1)"
  info "上传部署脚本（$size）到 $REMOTE_SCRIPTS"
  if is_dry_run; then
    info "[dry-run] tar -C deploy -czf - scripts | ssh $TARGET 'tar -xzf - -C $REMOTE_STAGE'"
  else
    ssh "${SSH_OPTS[@]}" "$TARGET" "mkdir -p $REMOTE_STAGE"
    ssh "${SSH_OPTS[@]}" "$TARGET" "tar -xzf - -C $REMOTE_STAGE" < "$tgz"
  fi
  rm -f "$tgz"
}

upload_full_deploy() {
  local tgz size
  tgz="$(mktemp "${TMPDIR:-/tmp}/yudao-deploy.XXXXXX.tgz")"
  # deploy.env 含本机密码，绝不能上传
  tar -C "$DEPLOY_DIR" \
    --exclude=deploy.env \
    --exclude=deploy.env.local \
    -czf "$tgz" .
  size="$(du -h "$tgz" | cut -f1)"
  info "上传 deploy/ 模板与脚本（$size，已排除 deploy.env）到 $REMOTE_STAGE"
  if is_dry_run; then
    info "[dry-run] tar -C deploy ... | ssh $TARGET 'rm -rf $REMOTE_STAGE && mkdir -p $REMOTE_STAGE && tar -xzf - -C $REMOTE_STAGE'"
  else
    ssh "${SSH_OPTS[@]}" "$TARGET" "rm -rf $REMOTE_STAGE && mkdir -p $REMOTE_STAGE"
    ssh "${SSH_OPTS[@]}" "$TARGET" "tar -xzf - -C $REMOTE_STAGE" < "$tgz"
  fi
  rm -f "$tgz"
}

upload_file() {
  local src="$1" dst="$2" size
  if [ -f "$src" ]; then
    size="$(du -h "$src" | cut -f1)"
  else
    size="缺失"
    is_dry_run || die "上传源文件不存在：$src"
    warn "上传源文件不存在（dry-run 继续）：$src"
  fi
  info "上传文件（$size）：$src -> $TARGET:$dst"
  if is_dry_run; then
    info "[dry-run] scp $src $TARGET:$dst"
    return 0
  fi
  scp "${SCP_OPTS[@]}" "$src" "$TARGET:$dst"
}

# 上传含密码的文件：scp 后立即 chmod 600，避免 /tmp 下被其他本机用户读取。
upload_private_file() {
  local src="$1" dst="$2"
  upload_file "$src" "$dst"
  if is_dry_run; then
    info "[dry-run] ssh $TARGET 'chmod 600 $dst'"
    return 0
  fi
  ssh "${SSH_OPTS[@]}" "$TARGET" "chmod 600 $(printf '%q' "$dst")"
}

upload_dir() {
  local src="$1" dst="$2" label="$3" tgz size
  if [ ! -d "$src" ]; then
    is_dry_run || die "上传目录不存在：$src"
    warn "上传目录不存在（dry-run 继续）：$src"
    info "[dry-run] 跳过上传${label}：$src"
    return 0
  fi
  tgz="$(mktemp "${TMPDIR:-/tmp}/yudao-upload.XXXXXX.tgz")"
  tar -C "$src" -czf "$tgz" .
  size="$(du -h "$tgz" | cut -f1)"
  info "上传${label}（$size）到 $TARGET:$dst"
  if is_dry_run; then
    info "[dry-run] tar -C $src -czf - . | ssh $TARGET 'rm -rf $dst && mkdir -p $dst && tar -xzf - -C $dst'"
  else
    ssh "${SSH_OPTS[@]}" "$TARGET" "rm -rf $dst && mkdir -p $dst"
    ssh "${SSH_OPTS[@]}" "$TARGET" "tar -xzf - -C $dst" < "$tgz"
  fi
  rm -f "$tgz"
}

cleanup_remote() {
  local paths=""
  local p
  for p in "$@"; do
    paths+=" $(printf '%q' "$p")"
  done
  if is_dry_run; then
    info "[dry-run] ssh $TARGET 'rm -rf$paths'"
    return 0
  fi
  ssh "${SSH_OPTS[@]}" "$TARGET" "rm -rf$paths" || warn "清理远端临时文件失败（可忽略）"
}

# ---------- 构建 ----------
find_pnpm() {
  if command -v pnpm >/dev/null 2>&1; then
    printf 'pnpm\n'
  elif command -v pnpm.cmd >/dev/null 2>&1; then
    printf 'pnpm.cmd\n'
  else
    printf '\n'
  fi
}

build_backend() {
  if [ "${SKIP_BUILD:-0}" -eq 1 ]; then
    info "已指定 --skip-build，跳过后端构建"
    return 0
  fi
  if is_dry_run; then
    info "[dry-run] (cd $REPO_ROOT && mvn -B clean package -pl yudao-server -am -Dmaven.test.skip=true)"
    return 0
  fi
  require_cmd mvn
  info "构建后端 jar（mvn）"
  # 显式从构建进程环境移除 sudo 密码，避免泄露给 Maven 及其子进程。
  ( unset BOARD_SUDO_PASSWORD
    cd "$REPO_ROOT" && env -u BOARD_SUDO_PASSWORD mvn -B clean package -pl yudao-server -am -Dmaven.test.skip=true )
}

ensure_backend_artifact() {
  if is_dry_run; then
    return 0
  fi
  [ -f "$BACKEND_JAR_LOCAL" ] || die "后端产物不存在：$BACKEND_JAR_LOCAL"
}

build_frontend() {
  if [ "${SKIP_BUILD:-0}" -eq 1 ]; then
    info "已指定 --skip-build，跳过前端构建"
    return 0
  fi
  if [ -d "$FRONTEND_APP/node_modules" ] || [ -f "$FRONTEND_APP/package.json" ]; then
    :
  else
    die "前端应用目录不存在：$FRONTEND_APP"
  fi
  if is_dry_run; then
    info "[dry-run] (cd $FRONTEND_APP && pnpm build)"
    return 0
  fi
  PNPM_BIN="$(find_pnpm)"
  [ -n "$PNPM_BIN" ] || die "未找到 pnpm，请先安装（npm i -g pnpm）"
  info "构建前端 dist（pnpm build）"
  # 同上：构建进程不得看到 sudo 密码。
  ( unset BOARD_SUDO_PASSWORD
    cd "$FRONTEND_APP" && env -u BOARD_SUDO_PASSWORD "$PNPM_BIN" build )
}

ensure_frontend_artifact() {
  if is_dry_run; then
    return 0
  fi
  [ -d "$FRONTEND_DIST_LOCAL" ] || die "前端产物不存在：$FRONTEND_DIST_LOCAL"
  [ -f "$FRONTEND_DIST_LOCAL/index.html" ] || die "前端产物缺少 index.html：$FRONTEND_DIST_LOCAL"
}

ensure_collector_artifact() {
  if is_dry_run; then
    return 0
  fi
  [ -f "$COLLECTOR_LOCAL/main.py" ] || die "collector 目录缺少 main.py：$COLLECTOR_LOCAL"
}

# ---------- 单组件动作 ----------
do_backend() {
  local version="$1"
  local remote_jar="/tmp/yudao-server-$version.jar"
  local -a remote_args

  build_backend
  ensure_backend_artifact
  valid_version "$version" || die "非法版本号：$version"
  info "后端版本：$version"

  upload_scripts
  upload_file "$BACKEND_JAR_LOCAL" "$remote_jar"

  remote_args=(--jar "$remote_jar" --version "$version" --keep "$KEEP")
  [ "${NO_HEALTHCHECK:-0}" -eq 1 ] && remote_args+=(--no-healthcheck)
  remote_bash_sudo deploy-backend.sh "${remote_args[@]}"
  cleanup_remote "$remote_jar"
  info "后端发布完成：$version"
}

do_frontend() {
  local version="$1"
  local remote_dir="/tmp/yudao-web-$version"

  build_frontend
  ensure_frontend_artifact
  valid_version "$version" || die "非法版本号：$version"
  info "前端版本：$version"

  upload_scripts
  upload_dir "$FRONTEND_DIST_LOCAL" "$remote_dir" "前端 dist"

  remote_bash_sudo deploy-frontend.sh --dist "$remote_dir" --version "$version" --keep "$KEEP"
  cleanup_remote "$remote_dir"
  info "前端发布完成：$version"
}

do_collector() {
  local version="$1"
  local remote_dir="/tmp/yudao-collector-$version"

  ensure_collector_artifact
  valid_version "$version" || die "非法版本号：$version"
  info "collector 版本：$version"

  upload_scripts
  upload_dir "$COLLECTOR_LOCAL" "$remote_dir" "collector 代码"

  remote_bash_sudo deploy-collector.sh --src "$remote_dir" --version "$version" --keep "$KEEP"
  cleanup_remote "$remote_dir"
  info "collector 发布完成：$version"
}

# ---------- 子命令 ----------
cmd_backend() {
  parse_common_args "$@"
  set -- ${REMAIN[@]+"${REMAIN[@]}"}
  SKIP_BUILD=0
  VERSION=""
  KEEP=3
  NO_HEALTHCHECK=0
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --skip-build) SKIP_BUILD=1; shift ;;
      --version) [ "$#" -ge 2 ] || die_usage "--version 缺少参数"; VERSION="$2"; shift 2 ;;
      --keep) [ "$#" -ge 2 ] || die_usage "--keep 缺少参数"; KEEP="$2"; shift 2 ;;
      --no-healthcheck) NO_HEALTHCHECK=1; shift ;;
      *) die_usage "backend 未知参数：$1" ;;
    esac
  done
  validate_keep "$KEEP"
  init_remote
  if [ -z "$VERSION" ]; then
    VERSION="$(new_version "$BACKEND_JAR_LOCAL" 2>/dev/null || true)"
    [ -n "$VERSION" ] || VERSION="$(new_version)"
  fi
  do_backend "$VERSION"
}

cmd_frontend() {
  parse_common_args "$@"
  set -- ${REMAIN[@]+"${REMAIN[@]}"}
  SKIP_BUILD=0
  VERSION=""
  KEEP=3
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --skip-build) SKIP_BUILD=1; shift ;;
      --version) [ "$#" -ge 2 ] || die_usage "--version 缺少参数"; VERSION="$2"; shift 2 ;;
      --keep) [ "$#" -ge 2 ] || die_usage "--keep 缺少参数"; KEEP="$2"; shift 2 ;;
      *) die_usage "frontend 未知参数：$1" ;;
    esac
  done
  validate_keep "$KEEP"
  init_remote
  [ -n "$VERSION" ] || VERSION="$(new_version)"
  do_frontend "$VERSION"
}

cmd_collector() {
  parse_common_args "$@"
  set -- ${REMAIN[@]+"${REMAIN[@]}"}
  VERSION=""
  KEEP=3
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --version) [ "$#" -ge 2 ] || die_usage "--version 缺少参数"; VERSION="$2"; shift 2 ;;
      --keep) [ "$#" -ge 2 ] || die_usage "--keep 缺少参数"; KEEP="$2"; shift 2 ;;
      *) die_usage "collector 未知参数：$1" ;;
    esac
  done
  validate_keep "$KEEP"
  init_remote
  [ -n "$VERSION" ] || VERSION="$(new_version)"
  do_collector "$VERSION"
}

cmd_all() {
  parse_common_args "$@"
  set -- ${REMAIN[@]+"${REMAIN[@]}"}
  SKIP_BUILD=0
  SKIP_BACKEND=0
  SKIP_FRONTEND=0
  SKIP_COLLECTOR=0
  KEEP=3
  NO_HEALTHCHECK=0
  VERSION=""
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --skip-build) SKIP_BUILD=1; shift ;;
      --skip-backend) SKIP_BACKEND=1; shift ;;
      --skip-frontend) SKIP_FRONTEND=1; shift ;;
      --skip-collector) SKIP_COLLECTOR=1; shift ;;
      --version) [ "$#" -ge 2 ] || die_usage "--version 缺少参数"; VERSION="$2"; shift 2 ;;
      --keep) [ "$#" -ge 2 ] || die_usage "--keep 缺少参数"; KEEP="$2"; shift 2 ;;
      --no-healthcheck) NO_HEALTHCHECK=1; shift ;;
      *) die_usage "all 未知参数：$1" ;;
    esac
  done
  validate_keep "$KEEP"
  init_remote
  [ -n "$VERSION" ] || VERSION="$(new_version)"
  valid_version "$VERSION" || die "非法版本号：$VERSION"
  info "本次统一版本号：$VERSION"

  if [ "$SKIP_BACKEND" -eq 0 ]; then
    info "===== [1/3] backend ====="
    do_backend "$VERSION"
  else
    info "跳过 backend"
  fi
  if [ "$SKIP_FRONTEND" -eq 0 ]; then
    info "===== [2/3] frontend ====="
    do_frontend "$VERSION"
  else
    info "跳过 frontend"
  fi
  if [ "$SKIP_COLLECTOR" -eq 0 ]; then
    info "===== [3/3] collector ====="
    do_collector "$VERSION"
  else
    info "跳过 collector"
  fi
  info "all 完成：$VERSION"
}

cmd_rollback() {
  parse_common_args "$@"
  set -- ${REMAIN[@]+"${REMAIN[@]}"}
  COMP=""
  VERSION=""
  while [ "$#" -gt 0 ]; do
    case "$1" in
      backend|frontend|collector)
        [ -z "$COMP" ] || die_usage "只能指定一个组件（已指定：$COMP）"
        COMP="$1"
        shift
        ;;
      --version) [ "$#" -ge 2 ] || die_usage "--version 缺少参数"; VERSION="$2"; shift 2 ;;
      *) die_usage "rollback 未知参数：$1" ;;
    esac
  done
  [ -n "$COMP" ] || die_usage "rollback 缺少组件参数（backend / frontend / collector）"
  init_remote
  upload_scripts
  local -a remote_args=("$COMP")
  [ -n "$VERSION" ] && remote_args+=(--version "$VERSION")
  remote_bash_sudo rollback.sh "${remote_args[@]}"
  info "回滚请求已完成：$COMP${VERSION:+ -> $VERSION}"
}

cmd_bootstrap() {
  parse_common_args "$@"
  set -- ${REMAIN[@]+"${REMAIN[@]}"}
  SKIP_PACKAGES=0
  ALLOW_MOCK=0
  ENV_LOCAL=""
  KEEP_RELEASES=3
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --skip-packages) SKIP_PACKAGES=1; shift ;;
      --allow-mock) ALLOW_MOCK=1; shift ;;
      --env-file) [ "$#" -ge 2 ] || die_usage "--env-file 缺少参数"; ENV_LOCAL="$2"; shift 2 ;;
      --keep-releases) [ "$#" -ge 2 ] || die_usage "--keep-releases 缺少参数"; KEEP_RELEASES="$2"; shift 2 ;;
      *) die_usage "bootstrap 未知参数：$1" ;;
    esac
  done
  validate_keep "$KEEP_RELEASES"
  [ -n "$ENV_LOCAL" ] && [ ! -f "$ENV_LOCAL" ] && die "--env-file 文件不存在：$ENV_LOCAL"
  init_remote

  upload_full_deploy

  REMOTE_ENV=""
  if [ -n "$ENV_LOCAL" ]; then
    REMOTE_ENV="$REMOTE_STAGE/env.remote.upload"
    upload_private_file "$ENV_LOCAL" "$REMOTE_ENV"
  fi

  local -a remote_args=(--run --keep-releases "$KEEP_RELEASES")
  [ "$SKIP_PACKAGES" -eq 1 ] && remote_args+=(--skip-packages)
  [ "$ALLOW_MOCK" -eq 1 ] && remote_args+=(--allow-mock)
  [ -n "$REMOTE_ENV" ] && remote_args+=(--env-file "$REMOTE_ENV")
  local status=0
  remote_bash_sudo bootstrap-board.sh "${remote_args[@]}" || status=$?
  # 板端脚本自身也会清理；这里再兜底一次，失败时同样删除上传的密码副本。
  [ -n "$REMOTE_ENV" ] && cleanup_remote "$REMOTE_ENV"
  info "bootstrap 请求已完成（退出码 $status）"
  return "$status"
}

validate_keep() {
  case "${1:-}" in
    ""|*[!0-9]*) die "--keep 必须是正整数：${1:-}" ;;
  esac
  [ "$1" -ge 1 ] || die "--keep 至少为 1"
}

# ---------- 入口 ----------
if [ "$#" -eq 0 ]; then
  usage
  exit 0
fi

COMMAND="$1"
case "$COMMAND" in
  -h|--help)
    usage
    exit 0
    ;;
esac
shift

case "$COMMAND" in
  bootstrap) cmd_bootstrap "$@" ;;
  backend) cmd_backend "$@" ;;
  frontend) cmd_frontend "$@" ;;
  collector) cmd_collector "$@" ;;
  all) cmd_all "$@" ;;
  rollback) cmd_rollback "$@" ;;
  *)
    usage >&2
    die "未知子命令：$COMMAND（可用：bootstrap backend frontend collector all rollback）"
    ;;
esac
