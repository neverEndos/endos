"""``collector_sample_raw`` 按天分区的预创建维护。

设计文档要求按天 RANGE 分区，正式运行时由维护任务提前创建未来分区，
避免采样数据落进 ``collector_sample_raw_default``（DEFAULT 分区正常必须为空）。

本模块把“需要哪些分区、DDL 长什么样”做成纯函数，便于单元测试；真正的
数据库执行由 :class:`storage.PgStorage` 的 ``ensure_partitions`` 调用。

规则：

- 提前创建“今天起 ``partition_lead_days`` 天内”的日分区（含今天与第
  ``lead_days`` 天），``partition_lead_days`` 读取
  ``collector_retention_policy`` 中 ``data_class='sample_raw'`` 的配置，默认 3；
- ``CREATE TABLE IF NOT EXISTS ... PARTITION OF ...`` 幂等，重复执行无副作用；
- 失败只告警，不中断采集轮询。
"""

import logging
from datetime import date, datetime, timedelta, timezone

MAIN_TABLE = "public.collector_sample_raw"
PARTITION_PREFIX = "collector_sample_raw_p"
DEFAULT_LEAD_DAYS = 3
SHANGHAI = timezone(timedelta(hours=8), name="Asia/Shanghai")
LOGGER_NAME = "hsi"


def local_today() -> date:
    """按业务时区（Asia/Shanghai）返回今天。"""

    return datetime.now(SHANGHAI).date()


def partition_name(day: date) -> str:
    return f"{PARTITION_PREFIX}{day:%Y%m%d}"


def partition_bounds(day: date) -> tuple[str, str]:
    """分区边界：``[day 00:00, day+1 00:00)``，与 ``timestamp(3)`` 本地墙钟一致。"""

    start = day.strftime("%Y-%m-%d 00:00:00")
    end = (day + timedelta(days=1)).strftime("%Y-%m-%d 00:00:00")
    return start, end


def build_create_partition_sql(day: date) -> str:
    name = partition_name(day)
    start, end = partition_bounds(day)
    return (
        f"CREATE TABLE IF NOT EXISTS public.{name}\n"
        f"    PARTITION OF {MAIN_TABLE}\n"
        f"    FOR VALUES FROM ('{start}') TO ('{end}')"
    )


def missing_partition_days(
    today: date,
    lead_days: int,
    existing_names,
) -> list[date]:
    """返回今天起 ``lead_days`` 天内尚不存在的分区日期（升序）。

    ``lead_days=0`` 只覆盖今天；``lead_days=3`` 覆盖今天、+1、+2、+3 共 4 天。
    """

    if lead_days < 0:
        raise ValueError("lead_days 不能为负数")
    existing = set(existing_names or ())
    return [
        today + timedelta(days=offset)
        for offset in range(0, lead_days + 1)
        if partition_name(today + timedelta(days=offset)) not in existing
    ]


def read_lead_days(conn, default: int = DEFAULT_LEAD_DAYS) -> int:
    """读取 ``collector_retention_policy.partition_lead_days``；缺失时返回默认值。"""

    with conn.cursor() as cur:
        cur.execute(
            "SELECT partition_lead_days FROM public.collector_retention_policy"
            " WHERE data_class = 'sample_raw' AND deleted = 0"
            " ORDER BY id LIMIT 1"
        )
        row = cur.fetchone()
    if not row or row[0] is None:
        return default
    value = int(row[0])
    return value if value >= 0 else default


def fetch_existing_partition_names(conn) -> set[str]:
    """查询父表下已存在的分区名（含 DEFAULT 分区）。"""

    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT child.relname
            FROM pg_inherits
            JOIN pg_class parent ON parent.oid = pg_inherits.inhparent
            JOIN pg_class child ON child.oid = pg_inherits.inhrelid
            WHERE parent.relname = 'collector_sample_raw'
            """
        )
        return {str(row[0]) for row in cur.fetchall()}


def ensure_partitions(
    conn,
    *,
    today: date | None = None,
    lead_days: int | None = None,
    logger: logging.Logger | None = None,
) -> list[str]:
    """确保未来分区存在，返回本次实际执行的 DDL 分区名。

    调用方负责连接/事务与异常告警；本函数只做幂等 DDL。
    """

    log = logger or logging.getLogger(LOGGER_NAME)
    today = today or local_today()
    lead = read_lead_days(conn) if lead_days is None else lead_days
    existing = fetch_existing_partition_names(conn)
    missing = missing_partition_days(today, lead, existing)
    created: list[str] = []
    if not missing:
        log.info(
            "collector_sample_raw 分区已齐全（今天=%s，提前 %d 天）", today, lead
        )
        return created
    for day in missing:
        sql = build_create_partition_sql(day)
        with conn.cursor() as cur:
            cur.execute(sql)
        created.append(partition_name(day))
        log.info("已创建分区 %s", partition_name(day))
    return created
