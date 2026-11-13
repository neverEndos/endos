"""轮询器单元测试（使用假 transport，不依赖串口）。"""

import os
import struct
import sys
import unittest
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from hsi import cs1000, hlb1400  # noqa: E402
from hsi.frame import RESPONSE_ADDRESS, build_frame, parse_frame  # noqa: E402
from poll import (  # noqa: E402
    JsonlWriter,
    PollError,
    SensorStats,
    discover_rs485_flags,
    poll_sensor_once,
    run_loop,
    summarize,
)


def response(number, data):
    return parse_frame(build_frame(RESPONSE_ADDRESS, number, data))


def cs1000_measure(values=(100, 200, 300, 21, 22, 23, 24, 250, 11, 55)):
    return response(3, struct.pack("<10h", *values))


def hlb1400_measure(values=(100, 200, 300, 400, 55, 250)):
    return response(3, struct.pack("<6h", *values))


def status_ok():
    return response(1, b"\x00\x00\x00")


class FakeTransport:
    """按调用顺序返回预设帧或抛出预设异常。"""

    def __init__(self, handler):
        self.handler = handler
        self.requests = []
        self.flags = None
        self.flags_calls = []

    def set_rs485_flags(self, flags, *args, **kwargs):
        self.flags = flags
        self.flags_calls.append(flags)

    def transact(self, request, timeout=1.0):
        self.requests.append(request)
        return self.handler(request, self.flags, len(self.requests))


class ListWriter:
    def __init__(self):
        self.records = []
        self.line_count = 0

    def write(self, record):
        self.records.append(record)
        self.line_count += 1


class PollSensorOnceTest(unittest.TestCase):
    def test_success_first_attempt(self):
        transport = FakeTransport(lambda request, flags, seq: cs1000_measure())
        result = poll_sensor_once(transport, cs1000.CS1000(), timeout=0.1, retries=1)
        self.assertTrue(result["ok"])
        self.assertEqual(result["attempts"], 1)
        self.assertEqual(len(result["fields"]), 10)
        self.assertEqual(result["quality"], 0)
        self.assertEqual(result["format"], "0x16")
        self.assertIsInstance(result["latency_ms"], float)

    def test_retry_succeeds_after_timeout(self):
        state = {"count": 0}

        def handler(request, flags, seq):
            state["count"] += 1
            if state["count"] == 1:
                raise TimeoutError("wait STX timeout")
            return hlb1400_measure()

        transport = FakeTransport(handler)
        result = poll_sensor_once(transport, hlb1400.HLB1400(), timeout=0.1, retries=1)
        self.assertTrue(result["ok"])
        self.assertEqual(result["attempts"], 2)
        self.assertEqual(len(result["fields"]), 6)
        self.assertEqual(len(transport.requests), 2)

    def test_failure_after_retry(self):
        def handler(request, flags, seq):
            raise TimeoutError("no response")

        transport = FakeTransport(handler)
        result = poll_sensor_once(transport, hlb1400.HLB1400(), timeout=0.1, retries=1)
        self.assertFalse(result["ok"])
        self.assertEqual(result["attempts"], 2)
        self.assertIn("TimeoutError", result["error"])
        self.assertEqual(len(transport.requests), 2)

    def test_zero_retries(self):
        def handler(request, flags, seq):
            raise TimeoutError("no response")

        result = poll_sensor_once(
            FakeTransport(handler), cs1000.CS1000(), timeout=0.1, retries=0
        )
        self.assertFalse(result["ok"])
        self.assertEqual(result["attempts"], 1)


class DiscoverFlagsTest(unittest.TestCase):
    def test_finds_first_working_flags(self):
        def handler(request, flags, seq):
            if flags < 5:
                raise TimeoutError(f"flags {flags} 静默")
            return status_ok()

        transport = FakeTransport(handler)
        drivers = [cs1000.CS1000(), hlb1400.HLB1400()]
        flags = discover_rs485_flags(
            transport,
            drivers,
            candidates=(1, 3, 5, 7),
            timeout=0.1,
            attempts_per_flag=1,
        )
        self.assertEqual(flags, 5)
        self.assertEqual(transport.flags_calls, [1, 3, 5])
        self.assertEqual(transport.flags, 5)

    def test_first_frame_only_flag_rejected_and_reopens(self):
        state = {"calls": 0}
        reopen_calls = []

        def handler(request, flags, seq):
            if flags == 1:
                state["calls"] += 1
                if state["calls"] == 1:
                    return status_ok()
                raise TimeoutError("首帧后 TX 卡死")
            return status_ok()

        transport = FakeTransport(handler)
        flags = discover_rs485_flags(
            transport,
            [cs1000.CS1000(), hlb1400.HLB1400()],
            candidates=(1, 0),
            timeout=0.1,
            attempts_per_flag=2,
            reopen=lambda: reopen_calls.append(True),
        )
        self.assertEqual(flags, 0)
        self.assertEqual(transport.flags_calls, [1, 0])
        self.assertEqual(reopen_calls, [True])

    def test_all_flags_fail(self):
        def handler(request, flags, seq):
            raise TimeoutError("no response")

        transport = FakeTransport(handler)
        with self.assertRaises(PollError):
            discover_rs485_flags(
                transport,
                [cs1000.CS1000(), hlb1400.HLB1400()],
                candidates=(1, 3),
                timeout=0.1,
                attempts_per_flag=1,
            )

    def test_one_sensor_missing_relaxed_mode_uses_responsive_drivers(self):
        def handler(request, flags, seq):
            # CS1000 (0x61) 有应答，HLB1400 无应答
            if request[2] == 0x61:
                return status_ok()
            raise TimeoutError("HLB1400 无应答")

        transport = FakeTransport(handler)
        responsive: set[str] = set()
        flags = discover_rs485_flags(
            transport,
            [cs1000.CS1000(), hlb1400.HLB1400()],
            candidates=(1,),
            timeout=0.1,
            attempts_per_flag=1,
            responsive_out=responsive,
        )
        self.assertEqual(flags, 1)
        self.assertEqual(responsive, {"CS1000"})

    def test_one_sensor_missing_strict_mode_rejects_flags(self):
        def handler(request, flags, seq):
            if request[2] == 0x61:
                return status_ok()
            raise TimeoutError("HLB1400 无应答")

        transport = FakeTransport(handler)
        with self.assertRaises(PollError):
            discover_rs485_flags(
                transport,
                [cs1000.CS1000(), hlb1400.HLB1400()],
                candidates=(1,),
                timeout=0.1,
                attempts_per_flag=1,
                require_all=True,
            )

    def test_all_sensors_missing_still_rejects_flags(self):
        def handler(request, flags, seq):
            raise TimeoutError("no response")

        transport = FakeTransport(handler)
        with self.assertRaises(PollError):
            discover_rs485_flags(
                transport,
                [cs1000.CS1000(), hlb1400.HLB1400()],
                candidates=(1,),
                timeout=0.1,
                attempts_per_flag=1,
            )


class RunLoopTest(unittest.TestCase):
    def test_one_cycle_writes_both_sensors(self):
        def handler(request, flags, seq):
            if request[2] == 0x61:
                return cs1000_measure()
            return hlb1400_measure()

        stop_calls = {"count": 0}

        def should_stop():
            stop_calls["count"] += 1
            return stop_calls["count"] > 1

        transport = FakeTransport(handler)
        writer = ListWriter()
        stats = run_loop(
            transport,
            [cs1000.CS1000(), hlb1400.HLB1400()],
            writer=writer,
            interval_s=0.01,
            duration_s=None,
            timeout=0.1,
            retries=1,
            should_stop=should_stop,
        )
        self.assertEqual(writer.line_count, 2)
        self.assertEqual(stats["CS1000"].cycles, 1)
        self.assertEqual(stats["CS1000"].successes, 1)
        self.assertEqual(stats["HLB1400"].successes, 1)
        self.assertEqual(stats["CS1000"].field_counts, {10: 1})
        self.assertEqual(stats["HLB1400"].field_counts, {6: 1})
        record = writer.records[0]
        for key in ("timestamp", "sensor", "address", "fields", "quality", "latency_ms"):
            self.assertIn(key, record)
        self.assertEqual(record["sensor"], "CS1000")
        self.assertEqual(record["address"], 0x61)
        self.assertEqual(len(record["fields"]), 10)
        datetime.fromisoformat(record["timestamp"])  # 时间戳可解析

    def test_failed_sensor_not_written(self):
        def handler(request, flags, seq):
            if request[2] == 0x61:
                return cs1000_measure()
            raise TimeoutError("HLB1400 无应答")

        stop_calls = {"count": 0}

        def should_stop():
            stop_calls["count"] += 1
            return stop_calls["count"] > 1

        writer = ListWriter()
        stats = run_loop(
            FakeTransport(handler),
            [cs1000.CS1000(), hlb1400.HLB1400()],
            writer=writer,
            interval_s=0.01,
            duration_s=None,
            timeout=0.1,
            retries=1,
            should_stop=should_stop,
        )
        self.assertEqual(writer.line_count, 1)
        self.assertEqual(stats["CS1000"].successes, 1)
        self.assertEqual(stats["HLB1400"].failures, 1)
        self.assertEqual(stats["HLB1400"].last_error.startswith("TimeoutError"), True)


class SummaryTest(unittest.TestCase):
    def test_summary_shape_and_rates(self):
        stats = {
            "CS1000": SensorStats("CS1000", 10),
            "HLB1400": SensorStats("HLB1400", 6),
        }
        stats["CS1000"].cycles = 2
        stats["CS1000"].record_success(10.0, 10, 1)
        stats["CS1000"].record_success(20.0, 10, 2)
        stats["HLB1400"].cycles = 2
        stats["HLB1400"].record_success(12.0, 6, 1)
        stats["HLB1400"].record_failure(5.0, 2, "TimeoutError: x")

        summary = summarize(
            stats, flags=5, duration_s=10.5, rss_kb=1234, jsonl_lines=3
        )
        self.assertEqual(summary["rs485_flags"], 5)
        self.assertEqual(summary["poll_cycles"], 2)
        self.assertEqual(summary["jsonl_lines"], 3)
        self.assertEqual(summary["rss_kb"], 1234)
        cs = summary["sensors"]["CS1000"]
        self.assertEqual(cs["success_rate"], 1.0)
        self.assertEqual(cs["latency_ms_avg"], 15.0)
        self.assertEqual(cs["latency_ms_max"], 20.0)
        self.assertEqual(cs["retries"], 1)
        hlb = summary["sensors"]["HLB1400"]
        self.assertEqual(hlb["success_rate"], 0.5)
        self.assertEqual(hlb["failures"], 1)


if __name__ == "__main__":
    unittest.main()
