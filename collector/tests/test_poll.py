"""轮询器单元测试（使用假 transport，不依赖串口）。"""

import logging
import os
import struct
import sys
import unittest
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import seed  # noqa: E402
from hsi import cs1000, hlb1400  # noqa: E402
from hsi.frame import RESPONSE_ADDRESS, build_frame, parse_frame  # noqa: E402
from poll import (  # noqa: E402
    JsonlWriter,
    PollError,
    SensorStats,
    discover_rs485_flags,
    poll_cycle,
    poll_sensor_once,
    run_loop,
    summarize,
)
from storage import build_sample_rows, context_from_rows  # noqa: E402

DEVICE_ID = 7
SENSOR_ID = 12


def response(number, data):
    return parse_frame(build_frame(RESPONSE_ADDRESS, number, data))


def cs1000_measure(values=(100, 200, 300, 21, 22, 23, 24, 250, 11, 55)):
    return response(3, struct.pack("<10h", *values))


def hlb1400_measure(values=(100, 200, 300, 400, 55, 250)):
    return response(3, struct.pack("<6h", *values))


def hlb1400_extended_measure(values=(100, 200, 300, 55, 250, 111, 222, 333)):
    return response(3, struct.pack("<8h", *values))


def resolved_hlb_context():
    """把 HLB1400 的 6 个种子通道全部解析出 id（111..116）。"""

    rows = []
    hlb_channels = [
        channel
        for channel in seed.CHANNEL_SEEDS
        if channel.sensor_code == "hlb1400-01"
    ]
    for index, channel in enumerate(hlb_channels, start=1):
        rows.append(
            (
                "hlb1400-01",
                channel.channel_code,
                SENSOR_ID,
                110 + index,
                DEVICE_ID,
                {"field": channel.field, "fieldIndex": channel.field_index},
            )
        )
    return context_from_rows(DEVICE_ID, rows)


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


class ExtendedFormatEndToEndTest(unittest.TestCase):
    """0x12 全链路：poll_sensor_once → poll_cycle → JSONL/落库行。

    使用假 transport 返回 0x12 报文，验证 raw_by_field 不会被轮询层丢弃，
    从而不会退回 0x0E 的 field_index 顺序造成错位。
    """

    def _run_cycle(self, measure_frame):
        transport = FakeTransport(lambda request, flags, seq: measure_frame)
        context = resolved_hlb_context()
        writer = ListWriter()
        stats = {"HLB1400": SensorStats("HLB1400", hlb1400.HLB1400.field_count)}
        logger = logging.getLogger("test-poll-extended")
        records = poll_cycle(
            transport,
            [hlb1400.HLB1400()],
            stats,
            writer=writer,
            cycle=1,
            timeout=0.1,
            retries=0,
            logger=logger,
            channel_context=context,
        )
        return context, writer, records, logger

    def test_extended_format_maps_by_field_name_end_to_end(self):
        context, writer, records, logger = self._run_cycle(
            hlb1400_extended_measure()
        )
        self.assertEqual(writer.line_count, 1)
        record = writer.records[0]
        self.assertEqual(record["format"], "0x12")
        # 轮询层必须透传字段名映射（这是本轮修复的核心）。
        self.assertEqual(
            record["raw_by_field"]["saturation_pct"], 55
        )
        self.assertEqual(record["raw_by_field"]["temperature_c"], 250)
        self.assertNotIn("dc_change_pct", record["raw_by_field"])
        self.assertEqual(
            list(record["field_names"]), list(hlb1400.EXTENDED_FIELD_NAMES)
        )

        entries = {entry["channel_id"]: entry for entry in record["channels"]}
        # 0x12 中存在的 5 个旧通道按字段名取值；dc_change_pct(114) 不存在。
        self.assertEqual(sorted(entries), [111, 112, 113, 115, 116])
        self.assertEqual(entries[111]["raw_value"], 100)
        self.assertEqual(entries[111]["value"], 10.0)
        self.assertEqual(entries[112]["raw_value"], 200)
        self.assertEqual(entries[112]["value"], 20.0)
        self.assertEqual(entries[113]["raw_value"], 300)
        self.assertEqual(entries[113]["value"], 3.0)
        self.assertEqual(entries[115]["raw_value"], 55)
        self.assertEqual(entries[115]["value"], 55.0)
        self.assertEqual(entries[116]["raw_value"], 250)
        self.assertEqual(entries[116]["value"], 25.0)
        self.assertEqual(len(entries), 5)
        # raw_conductivity/raw_dc/raw_dac 未配置通道，不产生条目。
        for name in ("raw_conductivity", "raw_dc", "raw_dac"):
            self.assertIn(name, record["raw_by_field"])
            self.assertNotIn(name, {entry["field"] for entry in entries.values()})

        rows = build_sample_rows(
            context.bindings,
            records,
            datetime(2026, 11, 13, 20, 30),
            DEVICE_ID,
            logger=logger,
        )
        by_id = {row[0]: row for row in rows}
        self.assertEqual(sorted(by_id), [111, 112, 113, 115, 116])
        self.assertEqual(by_id[111][4], 100)
        self.assertEqual(by_id[111][3], 10.0)
        self.assertEqual(by_id[112][4], 200)
        self.assertEqual(by_id[112][3], 20.0)
        self.assertEqual(by_id[113][4], 300)
        self.assertEqual(by_id[113][3], 3.0)
        self.assertEqual(by_id[115][4], 55)
        self.assertEqual(by_id[115][3], 55.0)
        self.assertEqual(by_id[116][4], 250)
        self.assertEqual(by_id[116][3], 25.0)
        self.assertNotIn(114, by_id)

    def test_legacy_format_regression_on_same_chain(self):
        context, writer, records, logger = self._run_cycle(hlb1400_measure())
        record = writer.records[0]
        self.assertEqual(record["format"], "0x0E")
        entries = {entry["channel_id"]: entry for entry in record["channels"]}
        self.assertEqual(sorted(entries), [111, 112, 113, 114, 115, 116])
        self.assertEqual(entries[114]["field"], "dc_change_pct")
        self.assertEqual(entries[114]["raw_value"], 400)
        self.assertEqual(entries[114]["value"], 40.0)
        self.assertEqual(entries[116]["raw_value"], 250)
        self.assertEqual(entries[116]["value"], 25.0)

        rows = build_sample_rows(
            context.bindings,
            records,
            datetime(2026, 11, 13, 20, 30),
            DEVICE_ID,
            logger=logger,
        )
        by_id = {row[0]: row for row in rows}
        self.assertEqual(sorted(by_id), [111, 112, 113, 114, 115, 116])
        self.assertEqual(by_id[114][4], 400)
        self.assertEqual(by_id[114][3], 40.0)
        self.assertEqual(by_id[116][4], 250)
        self.assertEqual(by_id[116][3], 25.0)

    def test_poll_sensor_once_preserves_raw_by_field(self):
        result = poll_sensor_once(
            FakeTransport(lambda request, flags, seq: hlb1400_extended_measure()),
            hlb1400.HLB1400(),
            timeout=0.1,
            retries=0,
            logger=logging.getLogger("test-poll-extended"),
        )
        self.assertTrue(result["ok"])
        self.assertEqual(result["raw_by_field"]["saturation_pct"], 55)
        self.assertEqual(
            list(result["field_names"]), list(hlb1400.EXTENDED_FIELD_NAMES)
        )


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
