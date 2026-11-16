#!/usr/bin/env bash
# PostgreSQL 自定义格式备份；保留最近 N 份。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
backup-db.sh —— 备份单机 Compose PostgreSQL

不带任何参数时只显示本帮助并退出 0。

用法：
  sudo bash scripts/backup-db.sh --run [选项]

选项：
  --run          执行备份（防止误操作）
  --keep N       保留最近 N 份，默认 7，至少 1
  --dry-run      只打印动作
  -h, --help     显示本帮助并退出 0

备份写入 .env 的 BACKUP_DIR（默认 /opt/yudao/backups），文件权限 0600，
使用 pg_dump -Fc。恢复前必须停止后端并人工确认目标库；本脚本不会自动恢复。
EOF
}
[ "$#" -gt 0 ] || { usage; exit 0; }
case "${1:-}" in -h|--help) usage; exit 0 ;; esac
RUN=0 KEEP=7
while [ "$#" -gt 0 ]; do
  case "$1" in
    --run) RUN=1; shift ;;
    --keep) [ "$#" -ge 2 ] || die "--keep 缺少参数"; KEEP="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "未知参数：$1" ;;
  esac
done
[ "$RUN" -eq 1 ] || { usage >&2; die "必须显式指定 --run"; }
case "$KEEP" in ""|*[!0-9]*) die "--keep 必须是正整数" ;; esac
[ "$KEEP" -ge 1 ] || die "--keep 至少为 1"
load_compose_env
DEST="${BACKUP_DIR:-/opt/yudao/backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
FILE="$DEST/${POSTGRES_DB:-ruoyi-vue-pro}-$STAMP.dump"
TMP="$FILE.tmp.$$"
trap 'rm -f "$TMP" 2>/dev/null || true' EXIT
run install -d -m 0700 "$DEST"
info "备份数据库 ${POSTGRES_DB:-ruoyi-vue-pro} 到 $FILE"
if is_dry_run; then
  info "[dry-run] docker compose exec -T postgres pg_dump -Fc > $TMP"
else
  umask 077
  compose exec -T postgres pg_dump -U "${POSTGRES_USER:-yudao}" -d "${POSTGRES_DB:-ruoyi-vue-pro}" -Fc > "$TMP"
  [ -s "$TMP" ] || die "备份文件为空"
  mv -f "$TMP" "$FILE"
  chmod 600 "$FILE"
fi
if [ -d "$DEST" ]; then
  find "$DEST" -maxdepth 1 -type f -name "${POSTGRES_DB:-ruoyi-vue-pro}-*.dump" -printf '%f\n' \
    | sort -r | awk -v keep="$KEEP" 'NR > keep { print }' \
    | while IFS= read -r old; do run rm -f "$DEST/$old"; done
fi
info "数据库备份完成：$FILE"
