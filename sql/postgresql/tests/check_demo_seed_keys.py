# -*- coding: utf-8 -*-
"""交叉核对 collector/test/01_demo_seed.sql 与 collector/seed.py 的业务键一致性。

演示种子 SQL 是 seed.py 的 SQL 移植版，二者必须保持 1:1（设备/传感器/类型/模板/通道
的业务键、量程与开关），防止单边漂移。本脚本纯静态解析，无需数据库。

用法：
    python -I sql/postgresql/tests/check_demo_seed_keys.py <仓库根目录>

退出码：0 一致；1 存在漂移。
"""
import io
import re
import sys

ROW_RE = re.compile(
    r"^\s*\('(?P<type>[a-z0-9]+)', '(?P<sensor>[a-z0-9-]+)', '(?P<channel>[a-z0-9_]+)', "
    r"'(?P<name>[^']*)', '(?P<kind>number|enum)', (?P<unit>'[^']*'|NULL), '(?P<icon>[^']*)', "
    r"(?P<vmin>-?\d+(?:\.\d+)?), (?P<vmax>-?\d+(?:\.\d+)?), (?P<scale>\d+(?:\.\d+)?), "
    r"(?P<decimals>\d+), (?P<alarm>[01]), (?P<s_le>[01]), (?P<s_lw>[01]), "
    r"(?P<s_hw>[01]), (?P<s_he>[01]), (?P<address>\d+), (?P<fidx>\d+), (?P<sort>\d+), "
    r"'(?P<desc>[^']*)'\)"
)


def fail(msg):
    print("FAIL " + msg)
    return 1


def main():
    repo = sys.argv[1]
    seed_py = repo + "/collector/seed.py"
    demo_sql = repo + "/sql/postgresql/collector/test/01_demo_seed.sql"

    with io.open(demo_sql, encoding="utf-8") as f:
        sql_text = f.read()
    with io.open(seed_py, encoding="utf-8") as f:
        py_text = f.read()

    failures = 0

    # 1. 演示 SQL 中恰好 16 行种子（1 行/条通道）
    rows = []
    for line in sql_text.splitlines():
        m = ROW_RE.match(line)
        if m:
            rows.append(m.groupdict())
    if len(rows) != 16:
        failures += fail("演示种子行数=%d，期望 16" % len(rows))
    else:
        print("ok   演示种子行数=16")

    # 2. 业务键集合与 seed.py 一致（seed.py 的 _TEMPLATE_RANGES 是 (type, channel) 全集来源）
    sql_keys = {(r["type"], r["channel"]) for r in rows}
    py_range_keys = set(re.findall(r'\("(?P<t>[a-z0-9]+)", "(?P<c>[a-z0-9_]+)"\): \(', py_text))
    if sql_keys != py_range_keys:
        failures += fail(
            "模板业务键不一致：仅 SQL=%s，仅 seed.py=%s"
            % (sorted(sql_keys - py_range_keys), sorted(py_range_keys - sql_keys))
        )
    else:
        print("ok   模板业务键与 seed.py _TEMPLATE_RANGES 一致（16 个）")

    # 3. 量程、值类别与四档开关一致
    py_ranges = {}
    for m in re.finditer(r'\("(?P<t>[a-z0-9]+)", "(?P<c>[a-z0-9_]+)"\): \((?P<body>[^)]*)\)', py_text):
        py_ranges[(m.group("t"), m.group("c"))] = m.group("body")
    for r in rows:
        body = py_ranges.get((r["type"], r["channel"]))
        if body is None:
            continue
        parts = [p.strip() for p in body.split(",")]
        # ("cs1000","iso_4"): (8.0, 25.0, "number", 1, 1, 1, 1, 1)
        exp_min, exp_max = parts[0], parts[1]
        exp_kind = parts[2].strip('"')
        exp_alarm = parts[3]
        exp_switches = parts[4:8]
        if r["kind"] != exp_kind:
            failures += fail("%s/%s 值类别 SQL=%s seed.py=%s" % (r["type"], r["channel"], r["kind"], exp_kind))
        if r["alarm"] != exp_alarm:
            failures += fail("%s/%s alarm_enabled SQL=%s seed.py=%s" % (r["type"], r["channel"], r["alarm"], exp_alarm))
        if [r["s_le"], r["s_lw"], r["s_hw"], r["s_he"]] != exp_switches:
            failures += fail(
                "%s/%s 开关 SQL=%s%s%s%s seed.py=%s"
                % (r["type"], r["channel"], r["s_le"], r["s_lw"], r["s_hw"], r["s_he"], ",".join(exp_switches))
            )
        if abs(float(r["vmin"]) - float(exp_min)) > 1e-9 or abs(float(r["vmax"]) - float(exp_max)) > 1e-9:
            failures += fail(
                "%s/%s 量程 SQL=%s~%s seed.py=%s~%s"
                % (r["type"], r["channel"], r["vmin"], r["vmax"], exp_min, exp_max)
            )
    if failures == 0:
        print("ok   量程/值类别/四档开关全部一致")

    # 4. 设备/传感器业务键与连接参数一致
    for needle in ["'rk3568-hsi-01'", "'cs1000-01'", "'hlb1400-01'", "'cs1000'", "'hlb1400'",
                   "'/dev/ttyS4'", "'hsi_rs485'"]:
        if needle not in sql_text:
            failures += fail("演示种子缺少业务键 %s" % needle)
    print("ok   设备/传感器业务键存在性检查完成" if failures == 0 else "", end="")
    if failures == 0:
        print()

    if failures:
        print("\n%d 项不一致" % failures)
        return 1
    print("\n全部一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
