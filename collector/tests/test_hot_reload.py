"""DB 配置指纹与运行中热加载测试（不连接真实 DB/串口）。"""

import logging
import os
import sys
import unittest
from dataclasses import replace
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import poll  # noqa: E402
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
            on_structure_change=lambda old, new, candidates: transport.set_rs485_flags(
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

    def test_structure_failure_keeps_old_state_and_retries_next_cycle(self):
        manager = FakeDbManager()
        attempts = []

        def apply(old, new, candidates):
            attempts.append(new.rs485_flags)
            if len(attempts) == 1:
                raise RuntimeError("模拟 ioctl 失败")
            return candidates

        reloader = self.make_reloader(manager, on_structure_change=apply)
        old_context = reloader.context
        old_settings = reloader.settings
        old_drivers = reloader.drivers
        manager.context = replace(manager.context, rs485_flags=3, timeout_ms=2500)
        manager.reload_result = "structure"

        with self.assertLogs(LOGGER, level="INFO") as captured:
            self.assertEqual(reloader.before_cycle(force=True), "error")
        self.assertIs(reloader.context, old_context)
        self.assertIs(reloader.settings, old_settings)
        self.assertIs(reloader.drivers, old_drivers)
        self.assertFalse(any("配置热加载类型=" in line for line in captured.output))
        self.assertTrue(any("保留旧上下文" in line for line in captured.output))

        # 不依赖 force：失败后的下一轮必须立即重试，成功后才替换状态。
        self.assertEqual(reloader.before_cycle(), "structure")
        self.assertEqual(attempts, [3, 3])
        self.assertEqual(manager.reload_calls, 2)
        self.assertIs(reloader.context, manager.context)
        self.assertEqual(reloader.settings.timeout_s, 2.5)
        self.assertEqual([driver.name for driver in reloader.drivers], ["CS1000", "HLB1400"])

    def test_persistent_flags_failure_has_bounded_attempts_within_60_seconds(self):
        manager = FakeDbManager()
        clock = FakeClock()
        attempts = []

        def always_fail(old, new, candidates):
            attempts.append(clock.now)
            raise RuntimeError("持续 ioctl 失败")

        manager.context = replace(
            manager.context, poll_interval_ms=100, rs485_flags=3
        )
        reloader = self.make_reloader(
            manager, clock=clock, on_structure_change=always_fail
        )
        manager.reload_result = "structure"

        with self.assertLogs(LOGGER, level="ERROR"):
            self.assertEqual(reloader.before_cycle(force=True), "error")
            for tenth_second in range(1, 601):
                clock.now = tenth_second / 10.0
                reloader.before_cycle()

        # t=0 首次、下一轮 t=0.1 立即重试，之后约在 2.1/6.1/14.1/30.1 秒；
        # 下一次需再等 32 秒，落在 62.1 秒，因此 60 秒内总共最多 6 次。
        self.assertEqual(len(attempts), 6)
        self.assertEqual(manager.reload_calls, 6)
        self.assertLessEqual(attempts[-1], 31.0)

    def test_same_hardware_error_logs_only_when_backoff_level_changes(self):
        manager = FakeDbManager()
        clock = FakeClock()
        attempts = []

        def always_fail(old, new, candidates):
            attempts.append(clock.now)
            raise RuntimeError("同一个 ioctl 错误")

        manager.context = replace(
            manager.context, poll_interval_ms=100, rs485_flags=3
        )
        reloader = self.make_reloader(
            manager, clock=clock, on_structure_change=always_fail
        )
        manager.reload_result = "structure"
        with self.assertLogs(LOGGER, level="ERROR") as captured:
            reloader.before_cycle(force=True)
            for tenth_second in range(1, 2001):
                clock.now = tenth_second / 10.0
                reloader.before_cycle()

        # 首次 + 立即重试后的 2/4/8/16/32/60 秒升级，共 7 条；60 秒封顶后
        # 相同错误继续失败不再重复写 ERROR。200 秒内硬件尝试共 9 次。
        self.assertEqual(len(attempts), 9)
        self.assertEqual(len(captured.output), 7)
        self.assertIn("60.0 秒后重试", captured.output[-1])

    def test_success_resets_backoff_and_restores_normal_check_interval(self):
        manager = FakeDbManager()
        clock = FakeClock()
        attempts = []
        should_fail = {"value": True}

        def apply(old, new, candidates):
            attempts.append(clock.now)
            if should_fail["value"]:
                raise RuntimeError("临时 ioctl 失败")
            return candidates

        manager.context = replace(
            manager.context, poll_interval_ms=100, rs485_flags=3
        )
        reloader = self.make_reloader(manager, clock=clock, on_structure_change=apply)
        manager.reload_result = "structure"
        with self.assertLogs(LOGGER, level="ERROR"):
            reloader.before_cycle(force=True)  # t=0，首次失败
            clock.now = 0.1
            reloader.before_cycle()  # 立即重试仍失败，进入 2 秒退避
        clock.now = 1.0
        self.assertEqual(reloader.before_cycle(), "cooldown")
        should_fail["value"] = False
        clock.now = 2.1
        self.assertEqual(reloader.before_cycle(), "structure")
        self.assertEqual(len(attempts), 3)
        self.assertEqual(reloader._hardware_retry_failures, 0)
        self.assertIsNone(reloader._hardware_retry_at)

        manager.reload_result = "none"
        clock.now = 2.19
        reloader.before_cycle()
        self.assertEqual(manager.reload_calls, 3)
        clock.now = 2.21
        reloader.before_cycle()
        self.assertEqual(manager.reload_calls, 4)

    def test_all_sensors_disabled_keeps_loop_with_empty_driver_list(self):
        manager = FakeDbManager()
        reloader = self.make_reloader(manager)
        manager.context = replace(manager.context, active_sensor_codes=())
        manager.reload_result = "structure"

        with self.assertLogs(LOGGER, level="WARNING") as captured:
            self.assertEqual(reloader.before_cycle(force=True), "structure")
        self.assertEqual(reloader.drivers, [])
        self.assertTrue(any("没有已启用传感器" in line for line in captured.output))


class RunLoopHotReloadIntegrationTest(unittest.TestCase):
    def test_parameters_reuse_drivers_structure_rebuilds_and_interval_is_live(self):
        clock = FakeClock()
        manager = FakeDbManager()
        manager.reload_result = "none"
        reloader = ConfigReloader(
            db_manager=manager,
            context=manager.context,
            drivers=enabled_drivers(manager.context),
            driver_factory=enabled_drivers,
            logger=LOGGER,
            clock=clock,
        )
        original_drivers = list(reloader.drivers)
        observed = []
        clock.now = 60.0

        def fake_poll_cycle(transport, drivers, stats, **kwargs):
            observed.append(
                {
                    "drivers": list(drivers),
                    "interval": reloader.settings.interval_s,
                    "timeout": kwargs["timeout"],
                    "retries": kwargs["retries"],
                }
            )
            # 第一轮只改运行参数；第二轮改结构；第三轮无变化。
            if len(observed) == 1:
                manager.context = replace(
                    manager.context, poll_interval_ms=1000, timeout_ms=2000, retries=0
                )
                manager.reload_result = "parameters"
                clock.now = 120.0
            elif len(observed) == 2:
                manager.context = replace(
                    manager.context, active_sensor_codes=("cs1000-01",)
                )
                manager.reload_result = "structure"
                clock.now = 121.0
            return []

        with (
            patch.object(poll, "poll_cycle", side_effect=fake_poll_cycle),
            patch.object(poll, "_sleep_until", return_value=None),
        ):
            poll.run_loop(
                object(),
                list(reloader.drivers),
                interval_s=reloader.settings.interval_s,
                logger=LOGGER,
                config_reloader=reloader,
                should_stop=lambda: len(observed) >= 3,
            )

        self.assertEqual(manager.reload_calls, 3)
        self.assertIs(observed[0]["drivers"][0], original_drivers[0])
        # 参数变化不重建 driver，但 timeout/retries 当轮生效。
        self.assertIs(observed[1]["drivers"][0], original_drivers[0])
        self.assertEqual(observed[1]["interval"], 1.0)
        self.assertEqual(observed[1]["timeout"], 2.0)
        self.assertEqual(observed[1]["retries"], 0)
        # 结构变化才重建；新 1 秒轮询间隔同时成为默认配置检查间隔。
        self.assertEqual([d.name for d in observed[2]["drivers"]], ["CS1000"])
        self.assertIsNot(observed[2]["drivers"][0], original_drivers[0])


class ReloadStorage:
    def __init__(self):
        self.context = context()
        # database_context 模拟表中的最新值；context 是 PgStorage 的已提交缓存。
        # prepare 会恢复缓存，但不能把“数据库中的新值”一并覆盖，否则无法重试。
        self.database_context = self.context
        self.fingerprint = fingerprint()
        self.fingerprint_error = None
        self.load_context_calls = 0
        self.fingerprint_calls = 0
        self.connect_calls = 0
        self.closed = False

    def connect(self):
        self.connect_calls += 1
        self.closed = False
        return None

    def check_latest_table(self):
        return None

    def load_config_fingerprint(self):
        self.fingerprint_calls += 1
        if self.fingerprint_error is not None:
            raise self.fingerprint_error
        return self.fingerprint

    def load_context(self):
        self.load_context_calls += 1
        self.context = self.database_context
        return self.database_context

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

        # DB 读取失败已进入常规重连状态；恢复由 ensure_ready 统一完成。
        self.assertFalse(manager.ready)
        manager.retry_interval_s = 0.0
        storage.fingerprint_error = None
        storage.database_context = replace(old_context, timeout_ms=3000)
        storage.fingerprint = fingerprint("t2")
        self.assertTrue(manager.ensure_ready())
        self.assertEqual(manager.context.timeout_ms, 3000)

    def test_flags_failure_preserves_old_fingerprint_then_retry_commits(self):
        storage = ReloadStorage()
        manager = self.make_manager(storage)
        old_context = manager.context
        old_fingerprint = manager.config_snapshot()[1]
        reloader = ConfigReloader(
            db_manager=manager,
            context=old_context,
            drivers=enabled_drivers(old_context),
            driver_factory=enabled_drivers,
            logger=LOGGER,
        )
        old_settings = reloader.settings
        old_drivers = reloader.drivers
        attempts = []

        def apply(old, new, candidates):
            attempts.append(new.rs485_flags)
            if len(attempts) == 1:
                raise RuntimeError("模拟 TIOCSRS485 失败")
            return candidates

        reloader._on_structure_change = apply
        storage.database_context = replace(old_context, rs485_flags=5, timeout_ms=4000)
        storage.fingerprint = fingerprint("t2", sensor_version="t2")

        with self.assertLogs(LOGGER, level="INFO") as captured:
            self.assertEqual(reloader.before_cycle(force=True), "error")
        self.assertIs(manager.context, old_context)
        self.assertIs(storage.context, old_context)
        self.assertEqual(manager.config_snapshot(), (old_context, old_fingerprint))
        self.assertIs(reloader.context, old_context)
        self.assertIs(reloader.settings, old_settings)
        self.assertIs(reloader.drivers, old_drivers)
        self.assertFalse(any("配置热加载类型=" in line for line in captured.output))

        # 同一时钟立即进入下一轮：绕过普通检查间隔，准备并应用同一个 t2。
        self.assertEqual(reloader.before_cycle(), "structure")
        self.assertEqual(attempts, [5, 5])
        self.assertIs(manager.context, storage.context)
        self.assertEqual(manager.context.rs485_flags, 5)
        self.assertEqual(manager.config_snapshot()[1], storage.fingerprint)
        self.assertEqual(reloader.settings.timeout_s, 4.0)

        load_calls = storage.load_context_calls
        self.assertEqual(reloader.before_cycle(force=True), "none")
        self.assertEqual(storage.load_context_calls, load_calls)

    def test_db_disconnect_during_hardware_backoff_uses_db_retry_interval(self):
        storage = ReloadStorage()
        clock = FakeClock()
        manager = DatabaseManager(
            seed_enabled=False,
            storage_factory=lambda: storage,
            retry_interval_s=30.0,
            logger=LOGGER,
            clock=clock,
        )
        storage.database_context = replace(storage.context, poll_interval_ms=100)
        self.assertTrue(manager.ensure_ready())
        old_context = manager.context
        reloader = ConfigReloader(
            db_manager=manager,
            context=old_context,
            drivers=enabled_drivers(old_context),
            driver_factory=enabled_drivers,
            logger=LOGGER,
            clock=clock,
        )
        storage.database_context = replace(old_context, rs485_flags=5)
        storage.fingerprint = fingerprint("t2", sensor_version="t2")
        attempts = []
        should_fail = {"value": True}

        def apply(old, new, candidates):
            attempts.append(clock.now)
            if should_fail["value"]:
                raise RuntimeError("持续 flags 失败")
            return candidates

        reloader._on_structure_change = apply
        with self.assertLogs(LOGGER, level="ERROR"):
            reloader.before_cycle(force=True)  # t=0
            clock.now = 0.1
            reloader.before_cycle()  # 下一轮立即重试，随后进入 2 秒退避
        self.assertEqual(len(attempts), 2)

        # 模拟退避期间写库断连。硬件重试时间即使已过，也不能提前重连 DB。
        clock.now = 1.0
        manager._mark_unavailable(ConnectionError("DB 断开"))
        for now in (2.1, 10.0, 30.9):
            clock.now = now
            self.assertEqual(reloader.before_cycle(), "unavailable")
            self.assertEqual(storage.connect_calls, 1)
            self.assertEqual(len(attempts), 2)

        should_fail["value"] = False
        clock.now = 31.0
        self.assertEqual(reloader.before_cycle(), "structure")
        self.assertEqual(storage.connect_calls, 2)
        self.assertEqual(len(attempts), 3)
        self.assertEqual(reloader._hardware_retry_failures, 0)

    def test_recovery_apply_failure_restores_pre_recovery_snapshot_and_retries(self):
        storage = ReloadStorage()
        manager = self.make_manager(storage)
        manager.retry_interval_s = 0.0
        old_context = manager.context
        old_fingerprint = manager.config_snapshot()[1]
        reloader = ConfigReloader(
            db_manager=manager,
            context=old_context,
            drivers=enabled_drivers(old_context),
            driver_factory=enabled_drivers,
            logger=LOGGER,
        )
        manager._mark_unavailable(ConnectionError("模拟运行中断线"))
        self.assertIs(manager.context, old_context)
        self.assertEqual(manager.config_snapshot(), (old_context, old_fingerprint))

        storage.database_context = replace(old_context, rs485_flags=3)
        storage.fingerprint = fingerprint("t2", sensor_version="t2")
        attempts = []

        def apply(old, new, candidates):
            attempts.append(new.rs485_flags)
            if len(attempts) == 1:
                raise RuntimeError("恢复后 flags 应用失败")
            return candidates

        reloader._on_structure_change = apply
        self.assertEqual(reloader.before_cycle(force=True), "error")
        self.assertEqual(manager.config_snapshot(), (old_context, old_fingerprint))
        self.assertIs(reloader.context, old_context)
        self.assertEqual(reloader.before_cycle(), "structure")
        self.assertEqual(attempts, [3, 3])
        self.assertEqual(manager.config_snapshot()[1], storage.fingerprint)


if __name__ == "__main__":
    unittest.main()
