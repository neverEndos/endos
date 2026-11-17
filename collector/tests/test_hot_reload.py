"""DB 配置指纹与运行中热加载测试（不连接真实 DB/串口）。"""

import logging
import os
import sys
import unittest
from dataclasses import replace

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from runtime_config import ConfigReloader  # noqa: E402
from storage import (  # noqa: E402
    DEVICE_CODE,
    ConfigFingerprint,
    DatabaseManager,
    DeviceContext,
    PgStorage,
)

LOGGER = logging.getLogger("test-hot-reload")
DRIVER_BY_SENSOR = {"cs1000-01": "CS1000", "hlb1400-01": "HLB1400"}


def context(
    *,
    interval_ms=60000,
    timeout_ms=1000,
    retries=1,
    active=("cs1000-01", "hlb1400-01"),
    rs485_flags=0,
):
    return DeviceContext(
        device_id=7,
        bindings=(),
        resolved=True,
        poll_interval_ms=interval_ms,
        timeout_ms=timeout_ms,
        retries=retries,
        rs485_flags=rs485_flags,
        active_sensor_codes=tuple(active),
    )


def fingerprint(version="t1", *, sensor_version="t1", channel_count=16):
    return ConfigFingerprint(version, 1, sensor_version, 2, "t1", channel_count)


class FakeDriver:
    def __init__(self, name):
        self.name = name
        self.field_count = 10 if name == "CS1000" else 6


def enabled_drivers(ctx):
    return [FakeDriver(DRIVER_BY_SENSOR[code]) for code in ctx.active_sensor_codes]


class FakeDbManager:
    def __init__(self):
        self.context = context()
        self.ready = True
        self.reload_result = "none"
        self.reload_calls = 0
        self.recover_context = None

    def ensure_ready(self):
        if not self.ready and self.recover_context is not None:
            self.context = self.recover_context
            self.ready = True
        return self.ready

    def reload_if_changed(self):
        self.reload_calls += 1
        return self.reload_result


class FakeTransport:
    """结构变化只允许设置 flags，不允许重开串口。"""

    def __init__(self):
        self.open_calls = 0
        self.close_calls = 0
        self.reopen_calls = 0
        self.flags_calls = []

    def set_rs485_flags(self, flags):
        self.flags_calls.append(flags)


class FakeClock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now


class FingerprintQueryTest(unittest.TestCase):
    def test_single_query_reads_three_table_versions_and_counts(self):
        class FakeCursor:
            def __init__(self):
                self.queries = []

            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, tb):
                return None

            def execute(self, sql, params):
                self.queries.append((sql, params))

            def fetchone(self):
                return ("t1", 1, "t2", 2, "t3", 16)

        class FakeConnection:
            closed = 0

            def __init__(self):
                self.fake_cursor = FakeCursor()

            def cursor(self):
                return self.fake_cursor

        connection = FakeConnection()
        storage = PgStorage(settings={"host": "fake"}, logger=LOGGER)
        storage._conn = connection
        result = storage.load_config_fingerprint()

        self.assertEqual(result.channel_count, 16)
        self.assertEqual(len(connection.fake_cursor.queries), 1)
        sql, params = connection.fake_cursor.queries[0]
        self.assertEqual(params, (DEVICE_CODE,) * 6)
        self.assertEqual(sql.count("COALESCE"), 3)
        self.assertEqual(sql.count("COUNT(*)"), 3)


class ConfigReloaderTest(unittest.TestCase):
    def make_reloader(self, manager, **kwargs):
        return ConfigReloader(
            db_manager=manager,
            context=manager.context,
            drivers=enabled_drivers(manager.context),
            driver_factory=enabled_drivers,
            logger=LOGGER,
            **kwargs,
        )

    def test_unchanged_fingerprint_does_not_reload_or_rebuild(self):
        manager = FakeDbManager()
        reloader = self.make_reloader(manager)
        original_drivers = reloader.drivers

        self.assertEqual(reloader.before_cycle(force=True), "none")
        self.assertEqual(manager.reload_calls, 1)
        self.assertIs(reloader.drivers[0], original_drivers[0])
        self.assertIs(reloader.context, manager.context)

    def test_parameter_change_only_updates_runtime_variables(self):
        manager = FakeDbManager()
        reloader = self.make_reloader(manager)
        original_drivers = reloader.drivers
        manager.context = replace(
            manager.context, poll_interval_ms=20000, timeout_ms=2500, retries=4
        )
        manager.reload_result = "parameters"

        self.assertEqual(reloader.before_cycle(force=True), "parameters")
        self.assertEqual(reloader.settings.interval_s, 20.0)
        self.assertEqual(reloader.settings.timeout_s, 2.5)
        self.assertEqual(reloader.settings.retries, 4)
        self.assertIs(reloader.drivers[0], original_drivers[0])
        self.assertIs(reloader.drivers[1], original_drivers[1])

    def test_structure_change_rebuilds_drivers_without_reopening_transport(self):
        manager = FakeDbManager()
        transport = FakeTransport()
        reloader = self.make_reloader(
            manager,
            on_structure_change=lambda old, new: transport.set_rs485_flags(
                new.rs485_flags
            ),
        )
        original_cs = reloader.drivers[0]
        manager.context = replace(
            manager.context,
            active_sensor_codes=("cs1000-01",),
            rs485_flags=3,
        )
        manager.reload_result = "structure"

        with self.assertLogs(LOGGER, level="INFO") as captured:
            self.assertEqual(reloader.before_cycle(force=True), "structure")
        self.assertEqual([driver.name for driver in reloader.drivers], ["CS1000"])
        self.assertIsNot(reloader.drivers[0], original_cs)
        self.assertEqual(transport.flags_calls, [3])
        self.assertEqual(transport.open_calls, 0)
        self.assertEqual(transport.close_calls, 0)
        self.assertEqual(transport.reopen_calls, 0)
        self.assertTrue(any("移出轮询集合" in line for line in captured.output))

    def test_db_unavailable_keeps_last_context_settings_and_drivers(self):
        manager = FakeDbManager()
        reloader = self.make_reloader(manager)
        old_context = reloader.context
        old_drivers = reloader.drivers
        old_settings = reloader.settings
        manager.ready = False

        self.assertEqual(reloader.before_cycle(force=True), "unavailable")
        self.assertIs(reloader.context, old_context)
        self.assertIs(reloader.drivers, old_drivers)
        self.assertEqual(reloader.settings, old_settings)

    def test_recovery_during_ensure_ready_adopts_reloaded_context(self):
        manager = FakeDbManager()
        reloader = self.make_reloader(manager)
        manager.ready = False
        manager.recover_context = replace(
            manager.context, active_sensor_codes=("cs1000-01",), timeout_ms=4000
        )

        self.assertEqual(reloader.before_cycle(force=True), "structure")
        self.assertEqual(reloader.settings.timeout_s, 4.0)
        self.assertEqual([driver.name for driver in reloader.drivers], ["CS1000"])

    def test_default_check_interval_follows_poll_interval(self):
        manager = FakeDbManager()
        clock = FakeClock()
        reloader = self.make_reloader(manager, clock=clock)

        clock.now = 59.0
        self.assertEqual(reloader.before_cycle(), "none")
        self.assertEqual(manager.reload_calls, 0)
        clock.now = 60.0
        self.assertEqual(reloader.before_cycle(), "none")
        self.assertEqual(manager.reload_calls, 1)


class ReloadStorage:
    def __init__(self):
        self.context = context()
        self.fingerprint = fingerprint()
        self.fingerprint_error = None
        self.load_context_calls = 0
        self.fingerprint_calls = 0
        self.closed = False

    def connect(self):
        return None

    def load_config_fingerprint(self):
        self.fingerprint_calls += 1
        if self.fingerprint_error is not None:
            raise self.fingerprint_error
        return self.fingerprint

    def load_context(self):
        self.load_context_calls += 1
        return self.context

    def close(self):
        self.closed = True


class DatabaseManagerFingerprintTest(unittest.TestCase):
    def make_manager(self, storage):
        manager = DatabaseManager(
            seed_enabled=False,
            storage_factory=lambda: storage,
            logger=LOGGER,
        )
        self.assertTrue(manager.ensure_ready())
        return manager

    def test_same_fingerprint_does_not_reload_context(self):
        storage = ReloadStorage()
        manager = self.make_manager(storage)
        initial_loads = storage.load_context_calls

        self.assertEqual(manager.reload_if_changed(), "none")
        self.assertEqual(storage.load_context_calls, initial_loads)

    def test_read_failure_keeps_last_valid_context_and_logs_warning(self):
        storage = ReloadStorage()
        manager = self.make_manager(storage)
        old_context = manager.context
        storage.fingerprint_error = ConnectionError("db down")

        with self.assertLogs(LOGGER, level="WARNING") as captured:
            self.assertEqual(manager.reload_if_changed(), "error")
        self.assertIs(manager.context, old_context)
        self.assertIs(storage.context, old_context)
        self.assertIn("保留上一次有效配置", captured.output[0])

        # DB 恢复后无需重启，下一次指纹检查即可加载新参数。
        storage.fingerprint_error = None
        storage.context = replace(old_context, timeout_ms=3000)
        storage.fingerprint = fingerprint("t2")
        self.assertEqual(manager.reload_if_changed(), "parameters")
        self.assertEqual(manager.context.timeout_ms, 3000)


if __name__ == "__main__":
    unittest.main()
