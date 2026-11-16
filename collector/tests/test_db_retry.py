"""DB 重连、重新种子与重新解析通道映射的单元测试（不连接真实数据库）。"""

import logging
import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import storage  # noqa: E402
from hsi import cs1000, hlb1400  # noqa: E402
from hsi.frame import RESPONSE_ADDRESS, build_frame, parse_frame  # noqa: E402
from poll import run_loop  # noqa: E402
from storage import DatabaseManager, unresolved_context  # noqa: E402


def resolved_context():
    """与真实 DB 一致的最小解析结果：设备 7 + 一个已解析通道。"""

    return storage.context_from_rows(
        7, [("cs1000-01", "iso_4", 11, 101, 7, {"fieldIndex": 1})]
    )


class FakeStorage:
    """替代 PgStorage：可控连接/映射/写入结果。"""

    def __init__(self, connect_error=None, context=None, write_error=None):
        self.connect_error = connect_error
        self.context = context if context is not None else resolved_context()
        self.write_error = write_error
        self.connect_calls = 0
        self.write_calls = 0
        self.heartbeat_calls = 0
        self.closed = False

    def connect(self):
        self.connect_calls += 1
        if self.connect_error is not None:
            raise self.connect_error

    def load_context(self):
        return self.context

    def write_cycle(self, results, collect_time=None, cycle=None):
        if self.write_error is not None:
            raise self.write_error
        self.write_calls += 1
        return {"written": 1, "skipped": False}

    def write_heartbeat(self):
        self.heartbeat_calls += 1
        return 1

    def close(self):
        self.closed = True


class StorageQueue:
    """按顺序创建 FakeStorage；用完后继续返回可用的默认实例。"""

    def __init__(self, specs):
        self.specs = list(specs)
        self.created = []

    def __call__(self):
        spec = self.specs.pop(0) if self.specs else {}
        instance = FakeStorage(**spec)
        self.created.append(instance)
        return instance


class FakeClock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now


class SeedRecorder:
    def __init__(self):
        self.calls = 0

    def __call__(self, **kwargs):
        self.calls += 1
        return {"device_id": 7}


def make_manager(*, clock, queue, retry_interval_s=60.0, seed_enabled=False, seed_func=None):
    return DatabaseManager(
        seed_enabled=seed_enabled,
        retry_interval_s=retry_interval_s,
        logger=logging.getLogger("test-db-retry"),
        storage_factory=queue,
        seed_func=seed_func,
        clock=clock,
    )


class DatabaseManagerTest(unittest.TestCase):
    def test_first_attempt_fails_then_recovers_after_interval(self):
        clock = FakeClock()
        queue = StorageQueue([{"connect_error": ConnectionError("db down")}])
        manager = make_manager(clock=clock, queue=queue)

        self.assertFalse(manager.ensure_ready())
        self.assertFalse(manager.ready)
        self.assertIsNone(manager.storage)
        self.assertIsNone(manager.context.device_id)
        self.assertEqual(len(queue.created), 1)
        self.assertTrue(queue.created[0].closed)
        self.assertEqual(manager.status()["attempts"], 1)

        # 节流：间隔未到时不再建连，JSONL 继续以 null id 输出。
        clock.now = 30.0
        self.assertFalse(manager.ensure_ready())
        self.assertEqual(len(queue.created), 1)
        skipped = manager.write_cycle([{"ok": True}], cycle=2)
        self.assertTrue(skipped["skipped"])
        self.assertEqual(len(queue.created), 1)

        # 到达重连间隔后自动恢复，并重新解析通道映射。
        clock.now = 60.0
        self.assertTrue(manager.ensure_ready())
        self.assertEqual(len(queue.created), 2)
        self.assertTrue(manager.ready)
        self.assertEqual(manager.context.device_id, 7)
        self.assertEqual(manager.storage, queue.created[1])

        outcome = manager.write_cycle([{"ok": True}], cycle=3)
        self.assertTrue(outcome["ready"])
        self.assertEqual(outcome["written"], 1)

    def test_mapping_failure_then_recovers(self):
        clock = FakeClock()
        queue = StorageQueue([{"context": unresolved_context()}, {}])
        manager = make_manager(clock=clock, queue=queue)

        # 首次连接成功但设备种子缺失 → 视为未就绪并关闭连接。
        self.assertFalse(manager.ensure_ready())
        self.assertTrue(queue.created[0].closed)
        self.assertFalse(manager.ready)
        self.assertIsNone(manager.storage)

        clock.now = 60.0
        self.assertTrue(manager.ensure_ready())
        self.assertEqual(len(queue.created), 2)
        self.assertEqual(manager.context.device_id, 7)
        by_key = {binding.key: binding for binding in manager.context.bindings}
        self.assertEqual(by_key[("cs1000-01", "iso_4")].channel_id, 101)

    def test_retry_is_throttled_by_interval(self):
        clock = FakeClock()
        queue = StorageQueue([{"connect_error": OSError("still down")} for _ in range(20)])
        manager = make_manager(clock=clock, queue=queue, retry_interval_s=60.0)

        for second in range(0, 60, 10):
            clock.now = float(second)
            self.assertFalse(manager.ensure_ready())
        # 0/10/20/30/40/50 秒共 6 次调用，只建立 1 次连接。
        self.assertEqual(len(queue.created), 1)
        self.assertEqual(manager.status()["attempts"], 1)

        clock.now = 60.0
        self.assertFalse(manager.ensure_ready())
        self.assertEqual(len(queue.created), 2)

    def test_same_error_only_warned_once(self):
        clock = FakeClock()
        queue = StorageQueue([{"connect_error": ConnectionError("db down")} for _ in range(3)])
        manager = make_manager(clock=clock, queue=queue)
        logger = logging.getLogger("test-db-retry")

        with self.assertLogs(logger, level="WARNING") as captured:
            self.assertFalse(manager.ensure_ready())
            clock.now = 60.0
            self.assertFalse(manager.ensure_ready())
            clock.now = 120.0
            self.assertFalse(manager.ensure_ready())
        self.assertEqual(len(captured.output), 1)
        self.assertIn("继续仅输出 JSONL", captured.output[0])

    def test_seed_runs_again_on_each_recovery_attempt(self):
        clock = FakeClock()
        seed = SeedRecorder()
        queue = StorageQueue([{"connect_error": ConnectionError("db down")}])
        manager = make_manager(clock=clock, queue=queue, seed_enabled=True, seed_func=seed)

        self.assertFalse(manager.ensure_ready())
        self.assertEqual(seed.calls, 1)

        clock.now = 60.0
        self.assertTrue(manager.ensure_ready())
        self.assertEqual(seed.calls, 2)
        self.assertTrue(manager.ready)

    def test_runtime_write_failure_uses_same_retry_gate(self):
        clock = FakeClock()
        queue = StorageQueue([{"write_error": RuntimeError("conn lost")}, {}])
        manager = make_manager(clock=clock, queue=queue)

        self.assertTrue(manager.ensure_ready())
        outcome = manager.write_cycle([{"ok": True}], cycle=4)
        self.assertTrue(outcome["skipped"])
        self.assertFalse(manager.ready)
        self.assertIsNone(manager.storage)
        self.assertTrue(queue.created[0].closed)
        self.assertEqual(queue.created[0].write_calls, 0)

        # 运行中失败与启动失败共用同一节流：间隔内不重连。
        clock.now = 30.0
        self.assertFalse(manager.ensure_ready())
        self.assertEqual(len(queue.created), 1)

        clock.now = 60.0
        self.assertTrue(manager.ensure_ready())
        self.assertEqual(len(queue.created), 2)
        outcome = manager.write_cycle([{"ok": True}], cycle=5)
        self.assertEqual(outcome["written"], 1)

    def test_heartbeat_skipped_when_unavailable(self):
        clock = FakeClock()
        queue = StorageQueue([{"connect_error": ConnectionError("db down")}])
        manager = make_manager(clock=clock, queue=queue)
        self.assertFalse(manager.ensure_ready())
        self.assertIsNone(manager.write_heartbeat())
        self.assertEqual(queue.created[0].heartbeat_calls, 0)

    def test_no_db_disables_attempts(self):
        clock = FakeClock()
        queue = StorageQueue([{}])
        manager = make_manager(clock=clock, queue=queue)
        manager.enabled = False
        self.assertFalse(manager.ensure_ready())
        self.assertFalse(manager.ready)
        self.assertEqual(queue.created, [])

    def test_runtime_mapping_recovery_updates_context_used_by_run_loop(self):
        """DB 第一轮不可用、随后恢复：JSONL 的 channel_id 会自动补上。"""

        clock = FakeClock()
        queue = StorageQueue([{"connect_error": ConnectionError("db down")}, {}])
        manager = make_manager(clock=clock, queue=queue, retry_interval_s=0.0)

        def handler(request, flags, seq):
            if request[2] == 0x61:
                return parse_frame(
                    build_frame(
                        RESPONSE_ADDRESS,
                        3,
                        struct.pack("<10h", 100, 200, 300, 21, 22, 23, 24, 250, 11, 55),
                    )
                )
            return parse_frame(
                build_frame(RESPONSE_ADDRESS, 3, struct.pack("<6h", 100, 200, 300, 400, 55, 250))
            )

        class FakeTransport:
            def __init__(self):
                self.flags = None

            def set_rs485_flags(self, flags):
                self.flags = flags

            def transact(self, request, timeout=1.0):
                return handler(request, self.flags, 0)

        class ListWriter:
            def __init__(self):
                self.records = []
                self.line_count = 0

            def write(self, record):
                self.records.append(record)
                self.line_count += 1

        writer = ListWriter()
        run_loop(
            FakeTransport(),
            [cs1000.CS1000(), hlb1400.HLB1400()],
            writer=writer,
            interval_s=0.01,
            timeout=0.1,
            retries=0,
            should_stop=lambda: writer.line_count >= 4,
            db_manager=manager,
        )

        self.assertEqual(writer.line_count, 4)
        # 第一轮（前两条）DB 不可用，channel_id 为 null。
        self.assertIsNone(writer.records[0]["channels"][0]["channel_id"])
        self.assertIsNone(writer.records[1]["channels"][0]["channel_id"])
        # 第二轮已恢复：使用重新解析后的映射。
        self.assertEqual(writer.records[2]["channels"][0]["channel_id"], 101)


if __name__ == "__main__":
    unittest.main()
