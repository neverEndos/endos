"""HLB1400 传感器驱动（HSI，地址 0x63）。

- 状态查询 msg=1；
- 测量值 msg=3，按应答 Length 分支：
  - Length=0x0E（旧格式）：6 个 int16（小端）——
    电导率 ×0.1 nS/m、相对电导率变化 ×0.1 %、DC ×0.01、
    相对 DC 变化 ×0.1 %、饱和度 %、温度 ×0.1 ℃；
  - Length=0x12（release 13.08 扩展格式）：8 个 int16（小端）——
    电导率 ×0.1、相对电导率变化 ×0.1 %、DC ×0.01、饱和度 %、温度 ×0.1 ℃、
    Raw Conductivity、Raw DC、Raw DAC。

扩展格式的字段顺序按任务卡给定的“8 字段、含 Raw Conductivity/Raw DC/Raw DAC”
整理；当前现场传感器只返回旧格式，扩展分支以单元测试覆盖。
"""

import struct

from hsi.frame import FrameError, HsiFrame, build_frame, validate_response

SENSOR_NAME = "HLB1400"
ADDRESS = 0x63
LEGACY_LENGTH = 0x0E
EXTENDED_LENGTH = 0x12
LEGACY_FIELD_COUNT = 6
EXTENDED_FIELD_COUNT = 8
STATUS_LENGTH = 0x05

# 每种应答格式的字段名与 Data 中 int16 的顺序一一对应。
# 落库/JSONL 的通道映射按字段名关联，不再依赖固定的 fieldIndex 顺序；
# 这样 0x0E（6 字段）与 0x12（8 字段）即使顺序不同也不会错位。
LEGACY_FIELD_NAMES = (
    "conductivity_ns_m",
    "conductivity_change_pct",
    "dc",
    "dc_change_pct",
    "saturation_pct",
    "temperature_c",
)

EXTENDED_FIELD_NAMES = (
    "conductivity_ns_m",
    "conductivity_change_pct",
    "dc",
    "saturation_pct",
    "temperature_c",
    "raw_conductivity",
    "raw_dc",
    "raw_dac",
)


def _scaled(value: int, factor: float, digits: int) -> float:
    result = round(value * factor, digits)
    return 0.0 if result == 0 else result


def build_status_request() -> bytes:
    """msg=1 状态查询请求。"""
    return build_frame(ADDRESS, 1)


def build_measure_request() -> bytes:
    """msg=3 测量值请求。"""
    return build_frame(ADDRESS, 3)


def parse_status(frame: HsiFrame) -> dict:
    """解析 msg=1 状态应答（Length=0x05，Data=StatusCodeLo/Hi+保留字节）。

    StatusCodeLo/Hi 组成 16 位状态码，0 表示有效；非 0 只记录，不在这里决定
    采集是否继续（是否跳过传感器由轮询器按统计策略判断）。
    """
    validate_response(frame, 1)
    if frame.length != STATUS_LENGTH:
        raise FrameError(
            f"HLB1400 状态应答 Length 错误: 0x{frame.length:02X}，"
            f"期望 0x{STATUS_LENGTH:02X}"
        )
    data = frame.data
    status_lo = data[0] if len(data) >= 1 else 0xFF
    status_hi = data[1] if len(data) >= 2 else 0x00
    status_code = status_lo | (status_hi << 8)
    return {
        "status_code_lo": status_lo,
        "status_code_hi": status_hi,
        "status_code": status_code,
        # 兼容旧字段：状态码低字节
        "status_byte": status_lo,
        "valid": status_code == 0,
        "raw_data_hex": frame.data.hex(),
    }


def parse_payload_legacy(data: bytes) -> dict:
    """解析 Length=0x0E 的旧格式负载（12 字节 / 6 个 int16）。"""
    if len(data) != LEGACY_FIELD_COUNT * 2:
        raise FrameError(
            f"HLB1400 旧格式负载长度错误: {len(data)} 字节，期望 {LEGACY_FIELD_COUNT * 2} 字节"
        )
    values = struct.unpack("<6h", data)
    fields = {
        "conductivity_ns_m": _scaled(values[0], 0.1, 1),
        "conductivity_change_pct": _scaled(values[1], 0.1, 1),
        "dc": _scaled(values[2], 0.01, 2),
        "dc_change_pct": _scaled(values[3], 0.1, 1),
        "saturation_pct": values[4],
        "temperature_c": _scaled(values[5], 0.1, 1),
    }
    return {
        "fields": fields,
        "raw_fields": list(values),
        "raw_by_field": dict(zip(LEGACY_FIELD_NAMES, values)),
        "field_names": LEGACY_FIELD_NAMES,
        "quality": 0,
        "format": f"0x{LEGACY_LENGTH:02X}",
    }


def parse_payload_extended(data: bytes) -> dict:
    """解析 Length=0x12 的 release 13.08 扩展格式负载（16 字节 / 8 个 int16）。

    注意 0x12 的字段顺序与 0x0E 不同（没有 dc_change_pct，且在温度之后追加
    Raw Conductivity/Raw DC/Raw DAC）。调用方必须按 ``raw_by_field`` 的字段名
    取值，不能沿用 0x0E 的 fieldIndex 顺序。
    """
    if len(data) != EXTENDED_FIELD_COUNT * 2:
        raise FrameError(
            f"HLB1400 扩展格式负载长度错误: {len(data)} 字节，期望 {EXTENDED_FIELD_COUNT * 2} 字节"
        )
    values = struct.unpack("<8h", data)
    fields = {
        "conductivity_ns_m": _scaled(values[0], 0.1, 1),
        "conductivity_change_pct": _scaled(values[1], 0.1, 1),
        "dc": _scaled(values[2], 0.01, 2),
        "saturation_pct": values[3],
        "temperature_c": _scaled(values[4], 0.1, 1),
        "raw_conductivity": values[5],
        "raw_dc": values[6],
        "raw_dac": values[7],
    }
    return {
        "fields": fields,
        "raw_fields": list(values),
        "raw_by_field": dict(zip(EXTENDED_FIELD_NAMES, values)),
        "field_names": EXTENDED_FIELD_NAMES,
        "quality": 0,
        "format": f"0x{EXTENDED_LENGTH:02X}",
    }


def parse_measure(frame: HsiFrame) -> dict:
    """按应答 Length 分支解析 msg=3 测量应答。"""
    validate_response(frame, 3)
    if frame.length == LEGACY_LENGTH:
        return parse_payload_legacy(frame.data)
    if frame.length == EXTENDED_LENGTH:
        return parse_payload_extended(frame.data)
    raise FrameError(
        f"HLB1400 测量应答 Length 不支持: 0x{frame.length:02X}"
        f"（仅支持 0x{LEGACY_LENGTH:02X}/0x{EXTENDED_LENGTH:02X}）"
    )


class HLB1400:
    """供轮询器使用的驱动对象。"""

    name = SENSOR_NAME
    address = ADDRESS
    field_count = LEGACY_FIELD_COUNT

    def build_status_request(self) -> bytes:
        return build_status_request()

    def build_measure_request(self) -> bytes:
        return build_measure_request()

    def parse_status(self, frame: HsiFrame) -> dict:
        return parse_status(frame)

    def parse_measure(self, frame: HsiFrame) -> dict:
        return parse_measure(frame)
