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
    """解析状态应答；与 CS1000 相同，取 Data 首字节作为 StatusByte。"""
    validate_response(frame, 1)
    status_byte = frame.data[0] if frame.data else 0xFF
    return {
        "status_byte": status_byte,
        "valid": status_byte == 0,
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
        "quality": 0,
        "format": f"0x{LEGACY_LENGTH:02X}",
    }


def parse_payload_extended(data: bytes) -> dict:
    """解析 Length=0x12 的 release 13.08 扩展格式负载（16 字节 / 8 个 int16）。"""
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
