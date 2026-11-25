#!/usr/bin/env bash
# deploy/tests/test_lf_normalize.sh —— normalize_text_lf 白名单 LF 归一化测试
#
# 用法：bash deploy/tests/test_lf_normalize.sh
# 覆盖：CRLF 的 shell/SQL/yml/conf/service 归一化为 LF（含 shebang 校验）、
#       .env 等非白名单文本与二进制字节不变、行内 CR 保留、幂等、dry-run、
#       以及模拟 bootstrap 的 tar 解包后归一化链路。全程只操作临时目录。
set -uo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../compose-single/scripts/lib/common.sh
source "$TEST_DIR/../compose-single/scripts/lib/common.sh"

TMP="$(mktemp -d "${TMPDIR:-/tmp}/yudao-lf-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
FAILURES=0

check_rc() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then
    printf 'ok   %s\n' "$label"
  else
    printf 'FAIL %s：期望 rc=%s，实际 rc=%s\n' "$label" "$expected" "$actual" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

# MSYS 版 grep 会对 CRLF 做透明处理导致漏检，统一用 tr 按字节判定。
has_cr() { [ -n "$(tr -cd '\r' < "$1" | head -c1)" ]; }

# 构造一套贴近 compose-single + sql/postgresql 布局的 CRLF 夹具。
build_fixture() {
  local root="$1"
  mkdir -p "$root/scripts/lib" "$root/init" "$root/nginx" "$root/systemd" \
    "$root/platform" "$root/collector" "$root/migration" \
    "$root/releases/backend" "$root/web"
  local f
  # 白名单文本：全部写成 CRLF；可执行脚本带 shebang。
  for f in scripts/bootstrap-board.sh scripts/lib/common.sh dbctl.sh; do
    printf '#!/usr/bin/env bash\r\n# 中文注释\r\necho "%s"\r\n' "$f" > "$root/$f"
  done
  printf '#!/bin/sh\r\nset -eu\r\npsql\r\n' > "$root/init/04a-collector-baseline-marker.sh"
  printf 'CREATE TABLE t (\r\n  id bigint\r\n);\r\n' > "$root/platform/ruoyi-vue-pro.sql"
  printf 'INSERT INTO t VALUES (1);\r\n' > "$root/collector/01_schema.sql"
  printf 'ALTER TABLE t ADD COLUMN c int;\r\n' > "$root/migration/m1.sql"
  printf 'services:\r\n  web:\r\n    image: nginx\r\n' > "$root/docker-compose.yml"
  printf 'key: value\r\nlist:\r\n  - a\r\n' > "$root/extra.yaml"
  printf 'server {\r\n  listen 80;\r\n}\r\n' > "$root/nginx/default.conf"
  printf '[Unit]\r\nDescription=collector\r\n[Service]\r\nExecStart=/usr/bin/python3 main.py\r\n' \
    > "$root/systemd/collector.service"
  # 行内 CR 必须保留，只允许去掉行尾 CR。
  printf "#!/usr/bin/env bash\r\nprintf 'a\\rb\\r\\n'\r\n" > "$root/scripts/inline-cr.sh"
  # 非白名单文本：即使含 CRLF 也不得改写（.env 为真实凭据，模板/文档同理）。
  printf 'POSTGRES_PASSWORD=secret\r\nREDIS_PASSWORD=secret\r\n' > "$root/.env"
  printf 'POSTGRES_PASSWORD=CHANGE_ME\r\n' > "$root/.env.example"
  printf 'BOARD_HOST=192.168.0.31\r\n' > "$root/deploy.env"
  printf '备注文本\r\n第二行\r\n' > "$root/notes.txt"
  printf '# 说明文档\r\n' > "$root/README.md"
  # 二进制样本：含 NUL、高位字节与 0d0a 序列，归一化前后必须逐字节一致。
  printf 'PK\x03\x04\r\n\x00\xff\xfe\r\ndata\r\n\x1f\x8b\x08\x00' > "$root/releases/backend/app.jar"
  printf '\x89PNG\r\n\x1a\n\x00\x00\r\n\x00IHDR' > "$root/web/logo.png"
  printf '\x1f\x8b\x08\x00\r\nbinary\r\n\x00\xff' > "$root/bundle.gz"
}

TREE="$TMP/tree"
build_fixture "$TREE"

# 先快照二进制，归一化后逐字节比对。
mkdir -p "$TMP/bin-snap"
cp -a "$TREE/releases/backend/app.jar" "$TMP/bin-snap/app.jar"
cp -a "$TREE/web/logo.png" "$TMP/bin-snap/logo.png"
cp -a "$TREE/bundle.gz" "$TMP/bin-snap/bundle.gz"

normalize_text_lf "$TREE" >/dev/null

# inline-cr.sh 含刻意保留的行内 CR，不在“完全无 CR”清单内，另有专门的字节断言。
WHITELISTED=(
  scripts/bootstrap-board.sh scripts/lib/common.sh dbctl.sh
  init/04a-collector-baseline-marker.sh
  platform/ruoyi-vue-pro.sql collector/01_schema.sql migration/m1.sql
  docker-compose.yml extra.yaml nginx/default.conf systemd/collector.service
)
for rel in "${WHITELISTED[@]}"; do
  if has_cr "$TREE/$rel"; then
    printf 'FAIL 白名单文件仍含 CR：%s\n' "$rel" >&2
    FAILURES=$((FAILURES + 1))
  else
    printf 'ok   已无 CR：%s\n' "$rel"
  fi
done

# shebang 必须以 bash\n / sh\n 结尾（若残留 \r，命令替换会保留它，等值断言失败）。
first="$(head -n1 "$TREE/dbctl.sh")"
if [ "$first" = '#!/usr/bin/env bash' ]; then
  printf 'ok   dbctl.sh shebang 以 bash\\n 结尾\n'
else
  printf 'FAIL dbctl.sh shebang：[%s]\n' "$first" >&2
  FAILURES=$((FAILURES + 1))
fi
sh_first="$(head -n1 "$TREE/init/04a-collector-baseline-marker.sh")"
if [ "$sh_first" = '#!/bin/sh' ]; then
  printf 'ok   init 脚本 shebang 以 sh\\n 结尾\n'
else
  printf 'FAIL init 脚本 shebang：[%s]\n' "$sh_first" >&2
  FAILURES=$((FAILURES + 1))
fi

# 行内 CR 保留、行尾 CR 去除。
printf "#!/usr/bin/env bash\nprintf 'a\\rb\\n'\n" > "$TMP/expect-inline"
if cmp -s "$TMP/expect-inline" "$TREE/scripts/inline-cr.sh"; then
  printf 'ok   行内 CR 保留且行尾 CR 去除\n'
else
  printf 'FAIL inline-cr.sh 字节不符合预期：\n' >&2
  od -c "$TREE/scripts/inline-cr.sh" >&2
  FAILURES=$((FAILURES + 1))
fi

# 非白名单文本保持 CRLF 不变。
for rel in .env .env.example deploy.env notes.txt README.md; do
  if has_cr "$TREE/$rel"; then
    printf 'ok   非白名单文本未改写：%s\n' "$rel"
  else
    printf 'FAIL 非白名单文本被错误改写：%s\n' "$rel" >&2
    FAILURES=$((FAILURES + 1))
  fi
done

# 二进制逐字节不变。
for pair in "releases/backend/app.jar:app.jar" "web/logo.png:logo.png" "bundle.gz:bundle.gz"; do
  src="${pair%%:*}"; snap="${pair##*:}"
  if cmp -s "$TMP/bin-snap/$snap" "$TREE/$src"; then
    printf 'ok   二进制字节不变：%s\n' "$src"
  else
    printf 'FAIL 二进制被改写：%s\n' "$src" >&2
    FAILURES=$((FAILURES + 1))
  fi
done

# 幂等：再跑一次，整树内容必须与快照完全一致。
cp -a "$TREE" "$TMP/tree-snap"
normalize_text_lf "$TREE" >/dev/null
if diff -r "$TMP/tree-snap" "$TREE" >/dev/null; then
  printf 'ok   重复归一化幂等\n'
else
  printf 'FAIL 二次归一化产生了差异\n' >&2
  FAILURES=$((FAILURES + 1))
fi

# dry-run：只打印不改写，CRLF 必须保留，且返回 0。
DRY="$TMP/dry"
build_fixture "$DRY"
rc=0
( DRY_RUN=1; normalize_text_lf "$DRY" ) >/dev/null || rc=$?
check_rc 0 "$rc" "dry-run 返回 0"
if has_cr "$DRY/dbctl.sh"; then
  printf 'ok   dry-run 不改写文件\n'
else
  printf 'FAIL dry-run 改写了文件\n' >&2
  FAILURES=$((FAILURES + 1))
fi

# 不存在的目录：告警跳过，不中断（返回 0）。
rc=0
normalize_text_lf "$TMP/does-not-exist" >/dev/null 2>&1 || rc=$?
check_rc 0 "$rc" "不存在目录跳过且返回 0"

# 缺少参数：die 退出非 0。
rc=0
( normalize_text_lf ) >/dev/null 2>&1 || rc=$?
if [ "$rc" -ne 0 ]; then
  printf 'ok   缺少参数返回非 0\n'
else
  printf 'FAIL 缺少参数未报错\n' >&2
  FAILURES=$((FAILURES + 1))
fi

# 集成链路：模拟 bootstrap 的 tar 管道解包后再归一化，文本 LF、二进制不变。
SRC="$TMP/src"; DST="$TMP/dst"
build_fixture "$SRC"
mkdir -p "$DST"
tar -C "$SRC" -cf - . | tar -C "$DST" -xf -
normalize_text_lf "$DST" >/dev/null
ok_tar=1
for rel in "${WHITELISTED[@]}"; do
  has_cr "$DST/$rel" && ok_tar=0
done
if [ "$ok_tar" -eq 1 ]; then
  printf 'ok   tar 解包后白名单文本全部 LF\n'
else
  printf 'FAIL tar 解包后仍有 CR 残留\n' >&2
  FAILURES=$((FAILURES + 1))
fi
if cmp -s "$TMP/bin-snap/app.jar" "$DST/releases/backend/app.jar" \
   && cmp -s "$TMP/bin-snap/logo.png" "$DST/web/logo.png" \
   && cmp -s "$TMP/bin-snap/bundle.gz" "$DST/bundle.gz"; then
  printf 'ok   tar 链路二进制字节不变\n'
else
  printf 'FAIL tar 链路二进制被改写\n' >&2
  FAILURES=$((FAILURES + 1))
fi

if [ "$FAILURES" -eq 0 ]; then
  printf '\n全部通过\n'
  exit 0
fi
printf '\n%d 项失败\n' "$FAILURES" >&2
exit 1
