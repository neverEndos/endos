#!/usr/bin/env bash
# 单机 Compose 板端脚本公共函数；由调用方 source，不单独执行。
COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="${COMPOSE_DIR:-$(cd "$COMMON_DIR/../.." && pwd)}"
DRY_RUN="${DRY_RUN:-0}"

log() { printf '%s\n' "$*"; }
info() { printf '[信息] %s\n' "$*"; }
warn() { printf '[警告] %s\n' "$*" >&2; }
error() { printf '[错误] %s\n' "$*" >&2; }
die() { error "$*"; exit 1; }
is_dry_run() { [ "$DRY_RUN" = "1" ]; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"; }

# ---------- 文本文件 LF 归一化 ----------
# Windows 开发机 core.autocrlf=true 时，工作区文本可能检出为 CRLF（deploy/ 下有
# .gitattributes 强制 LF，但 sql/postgresql/ 不在其覆盖范围内），经 tar/目录拷贝
# 到板端后会出现 shebang 的 bash\r 与 SQL/配置残留 CR。板端不一定安装 git，且
# 部署包可能来自非 git 来源，因此这里按文件内容做归一化，只认扩展名白名单：
# shell/SQL/Compose/nginx/systemd 文本；.env 真实凭据与 jar、图片、压缩包等二进制
# 不匹配白名单，字节保持不变。
LF_TEXT_FIND_ARGS=(
  -name '*.sh' -o -name '*.sql' -o -name '*.yml' -o -name '*.yaml'
  -o -name '*.conf' -o -name '*.service'
)

# 用法：normalize_text_lf <目录>...
# 递归处理目录下白名单扩展名的普通文件，只删除行尾 CR（CRLF -> LF），行内 CR 与
# 其他字节不变；不含 CR 的文件不重写，重复执行幂等。dry-run 只打印不改写。
# 路径不存在时告警并跳过，不因暂存目录缺项中断初始化。
normalize_text_lf() {
  local dir f
  [ "$#" -gt 0 ] || die "normalize_text_lf 缺少目录参数"
  for dir in "$@"; do
    if [ ! -d "$dir" ]; then
      warn "跳过不存在的 LF 归一化目录：$dir"
      continue
    fi
    while IFS= read -r -d '' f; do
      # 用 sed|cmp 按字节判断是否存在行尾 CR：部分平台（如 MSYS grep）会对
      # CRLF 做透明处理，不能依赖 grep 检测；无 CR 的文件直接跳过，避免无意义重写。
      if sed 's/\r$//' "$f" | cmp -s - "$f"; then
        continue
      fi
      if is_dry_run; then
        info "[dry-run] LF 归一化（CRLF -> LF）：$f"
        continue
      fi
      sed -i 's/\r$//' "$f"
      info "LF 归一化：$f"
    done < <(find "$dir" -type f \( "${LF_TEXT_FIND_ARGS[@]}" \) -print0)
  done
}

load_compose_env() {
  [ -f "$COMPOSE_DIR/.env" ] || die "缺少 $COMPOSE_DIR/.env，请先复制 .env.example 并修改"
  set -a
  # shellcheck disable=SC1091
  . "$COMPOSE_DIR/.env"
  set +a
}

compose() {
  docker compose --project-directory "$COMPOSE_DIR" -f "$COMPOSE_DIR/docker-compose.yml" "$@"
}

run() {
  if is_dry_run; then
    printf '[dry-run]'
    printf ' %q' "$@"
    printf '\n'
  else
    "$@"
  fi
}

valid_version() {
  case "${1:-}" in
    ""|.*|*[!A-Za-z0-9._-]*) return 1 ;;
  esac
}

new_version() {
  local sha=""
  if git -C "$COMPOSE_DIR" rev-parse --show-toplevel >/dev/null 2>&1; then
    sha="-$(git -C "$COMPOSE_DIR" rev-parse --short=8 HEAD)"
  fi
  printf '%s%s\n' "$(date +%Y%m%d-%H%M%S)" "$sha"
}

switch_symlink() {
  local target="$1" link="$2" tmp="$2.tmp.$$"
  if is_dry_run; then
    run ln -s "$target" "$tmp"
    run mv -Tf "$tmp" "$link"
    return 0
  fi
  rm -f -- "$link".tmp.* 2>/dev/null || true
  ln -s "$target" "$tmp"
  mv -Tf "$tmp" "$link"
}

wait_backend() {
  local timeout="${1:-240}" deadline body response_snippet=""
  if is_dry_run; then
    info "[dry-run] 从 web 容器请求后端 48080，校验 HTTP 与业务成功标识，等待上限 ${timeout}s"
    return 0
  fi
  require_cmd python3
  require_cmd mktemp
  body="$(mktemp "${TMPDIR:-/tmp}/yudao-backend-health.XXXXXX")"
  deadline=$((SECONDS + timeout))
  while [ "$SECONDS" -lt "$deadline" ]; do
    response_snippet=""
    # wget 负责确认 HTTP 成功；业务异常也可能返回 HTTP 200，必须再解析根节点。
    # 此接口声明 ResponseModel（成功 repCode="0000"）；兼容返回 CommonResult(code=0) 的版本。
    if compose exec -T web wget -T 5 -q -O - \
      --header='Content-Type: application/json' --post-data='{}' \
      http://yudao-server:48080/admin-api/system/captcha/get >"$body" 2>/dev/null; then
      if python3 -c 'import json, sys
try:
    with open(sys.argv[1], encoding="utf-8") as f:
        result = json.load(f)
except (OSError, UnicodeError, ValueError):
    sys.exit(1)
common_success = type(result) is dict and type(result.get("code")) is int and result["code"] == 0
captcha_success = type(result) is dict and result.get("repCode") == "0000"
sys.exit(0 if common_success or captcha_success else 1)' "$body"; then
        rm -f "$body"
        info "后端 48080 健康检查通过（HTTP 与业务成功标识均正常）"
        return 0
      fi
    fi
    response_snippet="$(head -c 1024 "$body" 2>/dev/null || true)"
    sleep 3
  done
  error "后端健康检查超时（${timeout}s）"
  if [ -n "$response_snippet" ]; then
    error "最后一次非空响应片段（未通过业务成功校验）："
    printf '%s\n' "$response_snippet" >&2
  fi
  rm -f "$body"
  return 1
}

wait_web() {
  local timeout="${1:-30}" deadline code
  if is_dry_run; then
    info "[dry-run] 请求 http://127.0.0.1:${WEB_PORT:-80}/，等待上限 ${timeout}s"
    return 0
  fi
  require_cmd python3
  deadline=$((SECONDS + timeout))
  while [ "$SECONDS" -lt "$deadline" ]; do
    code="$(python3 -c 'import sys,urllib.request
try:
 r=urllib.request.urlopen(sys.argv[1],timeout=3); r.read(16); print(r.status)
except Exception:
 sys.exit(1)' "http://127.0.0.1:${WEB_PORT:-80}/" 2>/dev/null || true)"
    if [ "$code" = "200" ]; then
      info "Web HTTP 200 检查通过"
      return 0
    fi
    sleep 2
  done
  error "Web 健康检查超时（${timeout}s）"
  return 1
}

prune_releases() {
  local dir="$1" keep="$2" current="$3"
  local versions=() v remove=0 total
  [ -d "$dir" ] || return 0
  while IFS= read -r v; do
    [ -n "$v" ] && versions+=("$v")
  done < <(find "$dir" -mindepth 1 -maxdepth 1 -type d ! -name '*.tmp.*' -printf '%f\n' | sort)
  total=${#versions[@]}
  [ "$total" -gt "$keep" ] || return 0
  for v in "${versions[@]}"; do
    [ "$total" -gt "$keep" ] || break
    if [ "$v" = "$current" ]; then
      continue
    fi
    info "清理旧版本：$dir/$v"
    run rm -rf "$dir/$v"
    remove=$((remove + 1))
    total=$((total - 1))
  done
}
