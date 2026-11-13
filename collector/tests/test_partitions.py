"""分区预创建纯函数与容错测试（不连接数据库）。"""

import os
import struct
import sys
import unittest
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import partitions  # noqa: E402
from hsi import cs1000, hlb1400  # noqa: E402
from hsi.frame import RESPONSE_ADDRESS, build_frame, parse_frame  # noqa: E402
from poll import run_loop  # noqa: E402


TODAY = date(2026, 11, 13)


class FakeCursor:
    def __init__(self, state):
        self.state = state
        self._rows = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, sql):
        self.state["executed"].append(sql)
        if "collector_retention_policy" in sql:
            self._rows = [(self.state.get("lead_days", 3),)]
        elif "pg_inherits" in sql:
            self._rows = [(name,) for name in self.state.get("existing", [])]
        elif sql.startswith("CREATE"):
            if self.state.get("fail_ddl"):
                raise PermissionError("permission denied for table collector_sample_raw")
        else:
            raise AssertionError(f"未预期的 SQL: {sql}")

    def fetchone(self):
        return self._rows[0] if self._rows else None

    def fetchall(self):
        return list(self._rows)


class FakeConn:
    def __init__(self, **state):
        self.state = {"executed": [], **state}

    def cursor(self):
        return FakeCursor(self.state)


class PartitionPureTest(unittest.TestCase):
    def test_partition_name_and_bounds(self):
        self.assertEqual(
            partitions.partition_name(TODAY), "collector_sample_raw_p20261113"
        )
        self.assertEqual(
            partitions.partition_bounds(TODAY),
            ("2026-11-13 00:00:00", "2026-11-14 00:00:00"),
        )

    def test_missing_days_cover_today_through_lead(self):
        existing = ["collector_sample_raw_p20261114"]
        missing = partitions.missing_partition_days(TODAY, 3, existing)
        self.assertEqual(
            [partitions.partition_name(day) for day in missing],
            [
                "collector_sample_raw_p20261113",
                "collector_sample_raw_p20261115",
                "collector_sample_raw_p20261116",
            ],
        )

    def test_missing_days_lead_zero_only_today(self):
        self.assertEqual(
            partitions.missing_partition_days(TODAY, 0, []),
            [TODAY],
        )

    def test_missing_days_all_exist(self):
        existing = [
            partitions.partition_name(TODAY),
            "collector_sample_raw_p20261114",
            "collector_sample_raw_p20261115",
            "collector_sample_raw_p20261116",
        ]
        self.assertEqual(partitions.missing_partition_days(TODAY, 3, existing), [])

    def test_create_ddl_content(self):
        sql = partitions.build_create_partition_sql(TODAY)
        self.assertIn(
            "CREATE TABLE IF NOT EXISTS public.collector_sample_raw_p20261113", sql
        )
        self.assertIn(
            "PARTITION OF public.collector_sample_raw", sql
        )
        self.assertIn(
            "FOR VALUES FROM ('2026-11-13 00:00:00') TO ('2026-11-14 00:00:00')", sql
        )


class PartitionEnsureTest(unittest.TestCase):
    def test_ensure_reads_policy_and_creates_missing(self):
        conn = FakeConn(lead_days=2, existing=["collector_sample_raw_p20261114"])
        created = partitions.ensure_partitions(conn, today=TODAY)
        self.assertEqual(
            created,
            ["collector_sample_raw_p20261113", "collector_sample_raw_p20261115"],
        )
        ddl = [sql for sql in conn.state["executed"] if sql.startswith("CREATE")]
        self.assertEqual(len(ddl), 2)

    def test_ensure_idempotent_when_nothing_missing(self):
        conn = FakeConn(
            lead_days=0,
            existing=["collector_sample_raw_p20261113"],
        )
        self.assertEqual(partitions.ensure_partitions(conn, today=TODAY), [])
        self.assertFalse(
            [sql for sql in conn.state["executed"] if sql.startswith("CREATE")]
        )

    def test_ensure_raises_on_permission_denied(self):
        conn = FakeConn(lead_days=0, existing=[], fail_ddl=True)
        with self.assertRaises(PermissionError):
            partitions.ensure_partitions(conn, today=TODAY)


def response(number, data):
    return parse_frame(build_frame(RESPONSE_ADDRESS, number, data))


class FakeTransport:
    def __init__(self):
        self.flags = None

    def set_rs485_flags(self, flags, *args, **kwargs):
        self.flags = flags

    def transact(self, request, timeout=1.0):
        if request[3] == 1:
            return response(1, b"\x00\x00\x00")
        if request[2] == 0x61:
            return response(3, struct.pack("<10h", *([0] * 10)))
        return response(3, struct.pack("<6h", *([0] * 6)))


class FailingPartitionManager:
    """DB 管理器替身：分区维护失败，但轮询与写入正常。"""

    def __init__(self):
        self.context = None
        self.ensure_calls = 0
        self.write_calls = 0

    def ensure_ready(self):
        return True

    def ensure_partitions(self):
        self.ensure_calls += 1
        raise PermissionError("permission denied for table collector_sample_raw")

    def write_cycle(self, results, collect_time=None, cycle=None):
        self.write_calls += 1
        return {"written": 0}

    def write_heartbeat(self):
        return 1


class PartitionFailureDoesNotStopLoopTest(unittest.TestCase):
    def test_ddl_failure_keeps_polling(self):
        manager = FailingPartitionManager()
        stop_calls = {"count": 0}

        def should_stop():
            stop_calls["count"] += 1
            return stop_calls["count"] > 1

        stats = run_loop(
            FakeTransport(),
            [cs1000.CS1000(), hlb1400.HLB1400()],
            interval_s=0.01,
            timeout=0.1,
            retries=0,
            should_stop=should_stop,
            db_manager=manager,
            partition_every=1,
        )
        self.assertEqual(manager.ensure_calls, 1)
        self.assertEqual(manager.write_calls, 1)
        self.assertEqual(stats["CS1000"].cycles, 1)
        self.assertEqual(stats["HLB1400"].cycles, 1)


if __name__ == "__main__":
    unittest.main()
