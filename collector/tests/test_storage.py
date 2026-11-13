"""种子映射、raw 行构造与 JSONL id 输出的单元测试（不连接数据库）。"""

import os
import struct
import sys
import unittest
from dataclasses import replace
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import seed  # noqa: E402
import storage  # noqa: E402
from hsi import cs1000, hlb1400  # noqa: E402
from hsi.frame import RESPONSE_ADDRESS, build_frame, parse_frame  # noqa: E402
from poll import run_loop  # noqa: E402
from storage import (  # noqa: E402
    ChannelBinding,
    DeviceContext,
    build_heartbeat_row,
    build_sample_rows,
    context_from_rows,
    cycle_outcome,
    next_device_state,
    seed_bindings,
    unresolved_context,
)

SENSOR_IDS = {"cs1000-01": 11, "hlb1400-01": 12}
DEVICE_ID = 7


def resolved_context() -> DeviceContext:
    bindings = []
    for index, binding in enumerate(seed_bindings(), start=1):
        bindings.append(
            replace(
                binding,
                channel_id=100 + index,
                sensor_id=SENSOR_IDS[binding.sensor_code],
                device_id=DEVICE_ID,
            )
        )
    return DeviceContext(device_id=DEVICE_ID, bindings=tuple(bindings), resolved=True)


def cs_result(values=(100, 200, 300, 21, 22, 23, 24, 250, 11, 55)):
    frame = parse_frame(
        build_frame(RESPONSE_ADDRESS, 3, struct.pack("<10h", *values))
    )
    return {
        "ok": True,
        "sensor": "CS1000",
        "sensor_code": "cs1000-01",
        "latency_ms": 8.0,
        **cs1000.parse_measure(frame),
    }


def hlb_result(values=(100, 200, 300, 400, 55, 250)):
    frame = parse_frame(
        build_frame(RESPONSE_ADDRESS, 3, struct.pack("<6h", *values))
    )
    return {
        "ok": True,
        "sensor": "HLB1400",
        "sensor_code": "hlb1400-01",
        "latency_ms": 9.0,
        **hlb1400.parse_measure(frame),
    }


def hlb_extended_result(values=(100, 200, 300, 55, 250, 111, 222, 333)):
    frame = parse_frame(
        build_frame(RESPONSE_ADDRESS, 3, struct.pack("<8h", *values))
    )
    return {
        "ok": True,
        "sensor": "HLB1400",
        "sensor_code": "hlb1400-01",
        "latency_ms": 9.0,
        **hlb1400.parse_measure(frame),
    }


def cs_frame_response():
    return parse_frame(
        build_frame(
            RESPONSE_ADDRESS, 3, struct.pack("<10h", 100, 200, 300, 21, 22, 23, 24, 250, 11, 55)
        )
    )


def hlb_frame_response():
    return parse_frame(
        build_frame(RESPONSE_ADDRESS, 3, struct.pack("<6h", 100, 200, 300, 400, 55, 250))
    )


class SeedLayoutTest(unittest.TestCase):
    def test_sixteen_channels_with_unique_keys(self):
        self.assertEqual(len(seed.CHANNEL_SEEDS), 16)
        self.assertEqual(len(seed.CHANNEL_SEED_BY_KEY), 16)
        cs = [c for c in seed.CHANNEL_SEEDS if c.sensor_code == "cs1000-01"]
        hlb = [c for c in seed.CHANNEL_SEEDS if c.sensor_code == "hlb1400-01"]
        self.assertEqual(len(cs), 10)
        self.assertEqual(len(hlb), 6)
        self.assertEqual([c.channel_code for c in cs][0], "iso_4")
        self.assertEqual([c.channel_code for c in cs][-1], "drive_pct")
        self.assertEqual([c.field_index for c in cs], list(range(1, 11)))
        self.assertEqual([c.field_index for c in hlb], list(range(1, 7)))
        for channel in seed.CHANNEL_SEEDS:
            self.assertEqual(channel.scale in (0.1, 0.01, 1.0), True)
            self.assertTrue(1 <= channel.field_index <= 10)

    def test_connection_config_matches_board(self):
        config = seed._connection_config(0x61)
        self.assertEqual(config["port"], "/dev/ttyS4")
        self.assertEqual(config["baud"], 9600)
        self.assertTrue(config["rs485"])
        self.assertEqual(config["rs485Flags"], 0)
        self.assertEqual(config["parity"], "none")

    def test_driver_name_mapping(self):
        self.assertEqual(seed.SENSOR_CODE_BY_DRIVER["CS1000"], "cs1000-01")
        self.assertEqual(seed.SENSOR_CODE_BY_DRIVER["HLB1400"], "hlb1400-01")


class ContextMergeTest(unittest.TestCase):
    def test_db_rows_fill_ids_and_keep_unresolved_none(self):
        rows = [
            ("cs1000-01", "iso_4", 11, 101, DEVICE_ID, {"fieldIndex": 1}),
            ("hlb1400-01", "saturation_pct", 12, 115, DEVICE_ID, {"fieldIndex": 5}),
        ]
        context = context_from_rows(DEVICE_ID, rows)
        self.assertTrue(context.resolved)
        self.assertEqual(context.device_id, DEVICE_ID)
        by_key = {b.key: b for b in context.bindings}
        self.assertEqual(by_key[("cs1000-01", "iso_4")].channel_id, 101)
        self.assertEqual(by_key[("hlb1400-01", "saturation_pct")].channel_id, 115)
        self.assertIsNone(by_key[("cs1000-01", "iso_6")].channel_id)
        self.assertEqual(len(context.missing_bindings()), 14)

    def test_channel_config_field_index_override(self):
        rows = [("cs1000-01", "iso_6", 11, 102, DEVICE_ID, {"fieldIndex": 4})]
        context = context_from_rows(DEVICE_ID, rows)
        binding = {b.key: b for b in context.bindings}[("cs1000-01", "iso_6")]
        self.assertEqual(binding.field_index, 4)

    def test_channel_config_json_string(self):
        rows = [("cs1000-01", "iso_14", 11, 103, DEVICE_ID, '{"fieldIndex": 2}')]
        context = context_from_rows(DEVICE_ID, rows)
        binding = {b.key: b for b in context.bindings}[("cs1000-01", "iso_14")]
        self.assertEqual(binding.field_index, 2)

    def test_unresolved_context_uses_seed_ids_none(self):
        context = unresolved_context()
        self.assertFalse(context.resolved)
        self.assertIsNone(context.device_id)
        self.assertEqual(len(context.bindings), 16)
        self.assertEqual(len(context.missing_bindings()), 16)


class BuildSampleRowsTest(unittest.TestCase):
    def test_cs1000_maps_engineering_and_raw_values(self):
        context = resolved_context()
        collect_time = datetime(2026, 11, 13, 20, 30, 0, 123000)
        rows = build_sample_rows(
            context.bindings, [cs_result()], collect_time, context.device_id
        )
        self.assertEqual(len(rows), 10)
        self.assertEqual(rows[0][0], 101)
        self.assertEqual(rows[0][1], DEVICE_ID)
        self.assertEqual(rows[0][2], collect_time)
        self.assertEqual(rows[0][3], 10.0)
        self.assertEqual(rows[0][4], 100)
        self.assertEqual(rows[0][5], 0)
        self.assertEqual(rows[9][0], 110)
        self.assertEqual(rows[9][3], 55)
        self.assertEqual(rows[9][4], 55)

    def test_hlb1400_maps_six_channels(self):
        context = resolved_context()
        rows = build_sample_rows(
            context.bindings,
            [hlb_result()],
            datetime(2026, 11, 13, 20, 30),
            context.device_id,
        )
        self.assertEqual(len(rows), 6)
        self.assertEqual(rows[0][0], 111)  # 排序后按 channel_id
        self.assertEqual(rows[0][3], 10.0)
        self.assertEqual(rows[0][4], 100)
        self.assertEqual(rows[5][3], 25.0)
        self.assertEqual(rows[5][4], 250)

    def test_failed_sensor_and_unresolved_channel_skipped(self):
        context = resolved_context()
        bindings = list(context.bindings)
        bindings[0] = replace(bindings[0], channel_id=None)
        rows = build_sample_rows(
            bindings,
            [
                {"ok": False, "sensor": "HLB1400", "raw_fields": [], "fields": {}},
                cs_result(),
            ],
            datetime(2026, 11, 13, 20, 30),
            DEVICE_ID,
        )
        self.assertEqual(len(rows), 9)
        self.assertNotIn(101, [row[0] for row in rows])

    def test_quality_from_parser(self):
        result = cs_result(values=(100, 200, 300, 21, 22, 23, 24, 250, 12, 55))
        self.assertEqual(result["quality"], 1)
        rows = build_sample_rows(
            resolved_context().bindings,
            [result],
            datetime(2026, 11, 13, 20, 30),
            DEVICE_ID,
        )
        self.assertTrue(all(row[5] == 1 for row in rows))

    def test_hlb1400_extended_maps_by_field_name_without_misalignment(self):
        # 0x12 顺序：conductivity/change/dc/saturation/temperature/raw...
        # 若沿用 0x0E 的 fieldIndex，dc_change(4) 会错拿 saturation，temperature(6)
        # 会错拿 raw_conductivity；本用例逐通道断言按字段名关联的结果。
        rows = build_sample_rows(
            resolved_context().bindings,
            [hlb_extended_result()],
            datetime(2026, 11, 13, 20, 30),
            DEVICE_ID,
        )
        by_id = {row[0]: row for row in rows}
        self.assertEqual(
            sorted(by_id),
            [111, 112, 113, 115, 116],
        )
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
        # 0x12 不存在 dc_change_pct，不能写入错位行。
        self.assertNotIn(114, by_id)
        # raw_conductivity/raw_dc/raw_dac 未配置通道，不产生行。
        self.assertEqual(len(rows), 5)

    def test_scale_and_offset_from_channel_config(self):
        # DB 列 scale=2.0 / value_offset=10 → 100*2+10=210
        rows = context_from_rows(
            DEVICE_ID,
            [
                (
                    "cs1000-01",
                    "iso_4",
                    11,
                    101,
                    DEVICE_ID,
                    {"fieldIndex": 1},
                    2.0,
                    10.0,
                    "µm",
                    1,
                )
            ],
        )
        result = build_sample_rows(
            rows.bindings, [cs_result()], datetime(2026, 11, 13, 20, 30), DEVICE_ID
        )
        first = {row[0]: row for row in result}[101]
        self.assertEqual(first[3], 210.0)
        self.assertEqual(first[4], 100)

    def test_disabled_channel_is_removed(self):
        context = context_from_rows(
            DEVICE_ID,
            [
                (
                    "cs1000-01",
                    "iso_4",
                    11,
                    101,
                    DEVICE_ID,
                    {"fieldIndex": 1},
                    0.1,
                    0.0,
                    "µm",
                    0,
                )
            ],
        )
        bindings = {binding.key: binding for binding in context.bindings}
        self.assertNotIn(("cs1000-01", "iso_4"), bindings)
        # 已解析上下文里把该通道标记为 disabled，落库时必须跳过。
        resolved = resolved_context()
        disabled = [
            replace(b, enabled=False) if b.key == ("cs1000-01", "iso_4") else b
            for b in resolved.bindings
        ]
        rows = build_sample_rows(
            disabled, [cs_result()], datetime(2026, 11, 13, 20, 30), DEVICE_ID
        )
        self.assertNotIn(101, [row[0] for row in rows])
        self.assertEqual(len(rows), 9)

    def test_device_poll_interval_and_rs485_flags_propagate(self):
        context = context_from_rows(
            DEVICE_ID,
            [("cs1000-01", "iso_4", 11, 101, DEVICE_ID, {"fieldIndex": 1})],
            poll_interval_ms=60000,
            rs485_flags=0,
            active_sensor_codes=("cs1000-01",),
        )
        self.assertEqual(context.poll_interval_ms, 60000)
        self.assertEqual(context.rs485_flags, 0)
        self.assertTrue(context.sensor_enabled("cs1000-01"))
        self.assertFalse(context.sensor_enabled("hlb1400-01"))

    def test_rs485_flags_from_connection_config(self):
        self.assertEqual(storage._rs485_flags_from_config({"rs485Flags": 3}), 3)
        self.assertEqual(
            storage._rs485_flags_from_config('{"rs485Flags": 0}'), 0
        )
        self.assertIsNone(storage._rs485_flags_from_config({"rs485Flags": "bad"}))
        self.assertIsNone(storage._rs485_flags_from_config("not-json"))


class DeviceStateTest(unittest.TestCase):
    def test_success_resets_failures(self):
        now = datetime(2026, 11, 13, 20, 30)
        state = next_device_state(
            0, 2, success=True, latency_ms=12, error=None, now=now
        )
        self.assertEqual(state.online, 1)
        self.assertEqual(state.consecutive_failures, 0)
        self.assertEqual(state.last_success_at, now)
        self.assertEqual(state.last_latency_ms, 12)
        self.assertIsNone(state.last_error)

    def test_three_consecutive_failures_mark_offline(self):
        now = datetime(2026, 11, 13, 20, 30)
        first = next_device_state(
            1, 0, success=False, latency_ms=None, error="timeout", now=now
        )
        self.assertEqual(first.online, 1)
        self.assertEqual(first.consecutive_failures, 1)
        second = next_device_state(
            1, 1, success=False, latency_ms=None, error="timeout", now=now
        )
        self.assertEqual(second.online, 1)
        self.assertEqual(second.consecutive_failures, 2)
        third = next_device_state(
            1, 2, success=False, latency_ms=None, error="timeout", now=now
        )
        self.assertEqual(third.online, 0)
        self.assertEqual(third.consecutive_failures, 3)
        self.assertEqual(third.last_offline_at, now)
        self.assertIsNone(third.last_latency_ms)

    def test_success_after_offline_sets_last_online_at(self):
        now = datetime(2026, 11, 13, 20, 30)
        state = next_device_state(
            0, 3, success=True, latency_ms=10, error=None, now=now
        )
        self.assertEqual(state.last_online_at, now)
        self.assertIsNone(state.last_offline_at)

    def test_cycle_outcome_uses_max_latency_and_any_success(self):
        success, latency, error = cycle_outcome(
            [
                {"ok": True, "sensor": "CS1000", "latency_ms": 8},
                {"ok": True, "sensor": "HLB1400", "latency_ms": 11},
            ]
        )
        self.assertTrue(success)
        self.assertEqual(latency, 11)
        self.assertIsNone(error)

        success, latency, error = cycle_outcome(
            [
                {"ok": True, "sensor": "CS1000", "latency_ms": 8},
                {"ok": False, "sensor": "HLB1400", "error": "TimeoutError: x"},
            ]
        )
        self.assertTrue(success)
        self.assertEqual(latency, 8)
        self.assertIn("HLB1400", error)


class HeartbeatRowTest(unittest.TestCase):
    def test_payload_keeps_resource_fields_empty(self):
        now = datetime(2026, 11, 13, 20, 30)
        row = build_heartbeat_row("ATK-DLRK3568-20261113T203000", now, 1, 15)
        self.assertEqual(row["status"], "running")
        self.assertEqual(row["device_total"], 1)
        self.assertEqual(row["device_online"], 1)
        self.assertEqual(row["write_lag_ms"], 15)
        self.assertEqual(row["collector_version"], "0.1.0-prototype")
        self.assertEqual(row["heartbeat_time"], now)
        for key in ("cpu_percent", "mem_percent", "disk_free_bytes", "disk_used_percent"):
            self.assertNotIn(key, row)


class FakeTransport:
    def set_rs485_flags(self, flags):
        self.flags = flags

    def transact(self, request, timeout=1.0):
        if request[2] == 0x61:
            return cs_frame_response()
        return hlb_frame_response()


class ListWriter:
    def __init__(self):
        self.records = []
        self.line_count = 0

    def write(self, record):
        self.records.append(record)
        self.line_count += 1


class JsonlChannelOutputTest(unittest.TestCase):
    def _run_one_cycle(self, context):
        writer = ListWriter()
        stop_calls = {"count": 0}

        def should_stop():
            stop_calls["count"] += 1
            return stop_calls["count"] > 1

        run_loop(
            FakeTransport(),
            [cs1000.CS1000(), hlb1400.HLB1400()],
            writer=writer,
            interval_s=0.01,
            duration_s=None,
            timeout=0.1,
            retries=1,
            should_stop=should_stop,
            channel_context=context,
        )
        return writer

    def test_jsonl_contains_resolved_device_and_channel_ids(self):
        writer = self._run_one_cycle(resolved_context())
        self.assertEqual(writer.line_count, 2)
        cs_record = writer.records[0]
        # 保留原字段
        for key in ("timestamp", "sensor", "address", "fields", "quality", "latency_ms"):
            self.assertIn(key, cs_record)
        self.assertEqual(cs_record["sensor"], "CS1000")
        self.assertEqual(cs_record["sensor_code"], "cs1000-01")
        self.assertEqual(cs_record["device_id"], DEVICE_ID)
        self.assertEqual(len(cs_record["channels"]), 10)
        first = cs_record["channels"][0]
        self.assertEqual(first["channel_id"], 101)
        self.assertEqual(first["channel_code"], "iso_4")
        self.assertEqual(first["value"], 10.0)
        self.assertEqual(first["raw_value"], 100)
        self.assertEqual(cs_record["raw"], [100, 200, 300, 21, 22, 23, 24, 250, 11, 55])

        hlb_record = writer.records[1]
        self.assertEqual(hlb_record["sensor_code"], "hlb1400-01")
        self.assertEqual(hlb_record["device_id"], DEVICE_ID)
        self.assertEqual(len(hlb_record["channels"]), 6)
        self.assertEqual(hlb_record["channels"][0]["channel_id"], 111)

    def test_unresolved_channel_still_output_with_null_id(self):
        context = resolved_context()
        bindings = list(context.bindings)
        bindings[0] = replace(bindings[0], channel_id=None)
        context = DeviceContext(context.device_id, tuple(bindings), True)
        writer = self._run_one_cycle(context)
        entry = writer.records[0]["channels"][0]
        self.assertIsNone(entry["channel_id"])
        self.assertEqual(entry["channel_code"], "iso_4")


if __name__ == "__main__":
    unittest.main()
