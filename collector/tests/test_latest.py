"""通道最新值构造与 raw/latest 同事务写入测试（不连接真实数据库）。"""

import logging
import os
import sys
import unittest
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import storage  # noqa: E402
from storage import ChannelBinding, DeviceContext, PgStorage  # noqa: E402


SAMPLE_TIME = datetime(2026, 11, 17, 12, 30, 0, 123000)


def make_binding(channel_id=101, value_kind="number"):
    return ChannelBinding(
        sensor_code="cs1000-01",
        channel_code="flow_state" if value_kind == "enum" else "iso_4",
        field="flow_state" if value_kind == "enum" else "iso_4",
        field_index=1,
        address=0x61,
        channel_id=channel_id,
        sensor_id=11,
        device_id=7,
        scale=0.1,
        value_kind=value_kind,
    )


def make_result():
    return {
        "ok": True,
        "sensor_code": "cs1000-01",
        "raw_by_field": {"iso_4": 125},
        "raw_fields": [125],
        "quality": 0,
    }


class LatestRowsTest(unittest.TestCase):
    def test_number_and_enum_use_independent_value_columns(self):
        bindings = [make_binding(101, "number"), make_binding(102, "enum")]
        sample_rows = [
            (101, 7, SAMPLE_TIME, 12.5, 125, 0),
            (102, 7, SAMPLE_TIME, 11.0, 11, 1),
        ]

        rows = storage.build_latest_rows(bindings, sample_rows)

        self.assertEqual(rows[0], (101, 7, 11, 12.5, None, 0, SAMPLE_TIME))
        self.assertEqual(rows[1], (102, 7, 11, None, "11", 1, SAMPLE_TIME))

    def test_context_reads_value_kind_without_breaking_old_rows(self):
        new_row = (
            "cs1000-01", "flow_state", 11, 102, 7,
            {"fieldIndex": 9}, 1.0, 0.0, None, 1, "enum",
        )
        old_row = ("cs1000-01", "iso_4", 11, 101, 7, {"fieldIndex": 1})

        context = storage.context_from_rows(7, [new_row, old_row])
        by_id = {binding.channel_id: binding for binding in context.bindings}

        self.assertEqual(by_id[102].value_kind, "enum")
        self.assertEqual(by_id[101].value_kind, "number")


class FakeCursor:
    def __init__(self, connection):
        self.connection = connection
        self.statements = []
        self.batches = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def execute(self, sql, params):
        self.statements.append((sql, params))

    def executemany(self, sql, rows):
        self.batches.append((sql, list(rows)))
        if self.connection.fail_latest and "collector_channel_latest" in sql:
            raise RuntimeError("模拟 latest 写入失败")


class FakeConnection:
    def __init__(self, fail_latest=False):
        self.closed = 0
        self.fail_latest = fail_latest
        self.fake_cursor = FakeCursor(self)
        self.commits = 0
        self.rollbacks = 0

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        if exc_type is None:
            self.commits += 1
        else:
            self.rollbacks += 1
        return False

    def cursor(self):
        return self.fake_cursor

    def close(self):
        self.closed = 1


class LatestTransactionTest(unittest.TestCase):
    def make_storage(self, connection):
        instance = PgStorage(
            settings={"host": "fake"}, logger=logging.getLogger("test-latest")
        )
        instance.context = DeviceContext(
            device_id=7, bindings=(make_binding(),), resolved=True
        )
        instance._conn = connection
        return instance

    def test_raw_and_latest_share_one_transaction_and_cursor(self):
        connection = FakeConnection()
        instance = self.make_storage(connection)

        outcome = instance.write_cycle([make_result()], SAMPLE_TIME, cycle=1)

        self.assertEqual(connection.commits, 1)
        self.assertEqual(connection.rollbacks, 0)
        self.assertEqual(outcome["written"], 1)
        self.assertEqual(outcome["latest_written"], 1)
        self.assertEqual(len(connection.fake_cursor.batches), 2)
        raw_sql, raw_rows = connection.fake_cursor.batches[0]
        latest_sql, latest_rows = connection.fake_cursor.batches[1]
        self.assertIn("collector_sample_raw", raw_sql)
        self.assertEqual(raw_rows[0][0], 101)
        self.assertIn("ON CONFLICT (channel_id) DO UPDATE", latest_sql)
        self.assertEqual(latest_rows, [(101, 7, 11, 12.5, None, 0, SAMPLE_TIME)])

    def test_latest_failure_rolls_back_raw_and_status(self):
        connection = FakeConnection(fail_latest=True)
        instance = self.make_storage(connection)

        with self.assertRaisesRegex(RuntimeError, "latest"):
            instance.write_cycle([make_result()], SAMPLE_TIME, cycle=2)

        self.assertEqual(connection.commits, 0)
        self.assertEqual(connection.rollbacks, 1)
        self.assertEqual(len(connection.fake_cursor.batches), 2)
        self.assertEqual(connection.closed, 1)

    def test_channel_without_current_value_does_not_upsert(self):
        connection = FakeConnection()
        instance = self.make_storage(connection)
        result = make_result()
        result["raw_by_field"] = {}
        result["raw_fields"] = []

        outcome = instance.write_cycle([result], SAMPLE_TIME, cycle=3)

        self.assertEqual(outcome["written"], 0)
        self.assertEqual(outcome["latest_written"], 0)
        self.assertEqual(connection.fake_cursor.batches, [])


if __name__ == "__main__":
    unittest.main()
