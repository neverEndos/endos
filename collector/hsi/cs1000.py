"""CS1000 传感器驱动（HSI，地址 0x61）。

- 状态查询 msg=1；
- 测量值 msg=3，应答 Length=0x16，10 个 int16（小端）：
  ISO4/ISO6/ISO14、SAE A/B/C/D 均 ×0.1；
  温度 ×0.1 ℃；
  FlowState 为枚举（11/22/33/44/55）；
  Drive 为百分比（1:1）。
"""

import struct

from hsi.frame import FrameError, HsiFrame, build_frame, validate_response

SENSOR_NAME = "CS1000"
ADDRESS = 0x61
MEASURE_LENGTH = 0x16
FIELD_COUNT = 10
VALID_FLOW_STATES = (11, 22, 33, 44, 55)

# 字段名 -> (缩放系数, 小数位)；FlowState/Drive 为整数，不做缩放。
_SCALED_FIELDS = (
    ("iso_4", 0.1, 1),
    ("iso_6", 0.1, 1),
    ("iso_14", 0.1, 1),
    ("sae_a", 0.1, 1),
    ("sae_b", 0.1, 1),
    ("sae_c", 0.1, 1),
    ("sae_d", 0.1, 1),
    ("temperature_c", 0.1, 1),
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
    """解析状态应答。

    协议只约定“无效时 StatusByte≠0”；原型取 Data 首字节作为 StatusByte，
    同时保留原始 Data 便于后续按正式文档校正。
    """
    validate_response(frame, 1)
    status_byte = frame.data[0] if frame.data else 0xFF
    return {
        "status_byte": status_byte,
        "valid": status_byte == 0,
        "raw_data_hex": frame.data.hex(),
    }


def parse_payload(data: bytes) -> dict:
    """解析 20 字节测量负载，返回字段字典、quality 与格式标识。"""
    if len(data) != FIELD_COUNT * 2:
        raise FrameError(
            f"CS1000 测量负载长度错误: {len(data)} 字节，期望 {FIELD_COUNT * 2} 字节"
        )
    values = struct.unpack("<10h", data)
    fields = {}
    for index, (name, factor, digits) in enumerate(_SCALED_FIELDS):
        fields[name] = _scaled(values[index], factor, digits)
    flow_state = values[8]
    drive = values[9]
    fields["flow_state"] = flow_state
    fields["drive_pct"] = drive
    # FlowState 不在协议枚举内视为质量异常，便于后续告警接入。
    quality = 0 if flow_state in VALID_FLOW_STATES else 1
    return {
        "fields": fields,
        "quality": quality,
        "format": f"0x{MEASURE_LENGTH:02X}",
    }


def parse_measure(frame: HsiFrame) -> dict:
    """校验并解析 msg=3 测量应答。"""
    validate_response(frame, 3)
    if frame.length != MEASURE_LENGTH:
        raise FrameError(
            f"CS1000 测量应答 Length 错误: 0x{frame.length:02X}，期望 0x{MEASURE_LENGTH:02X}"
        )
    return parse_payload(frame.data)


class CS1000:
    """供轮询器使用的驱动对象。"""

    name = SENSOR_NAME
    address = ADDRESS
    field_count = FIELD_COUNT

    def build_status_request(self) -> bytes:
        return build_status_request()

    def build_measure_request(self) -> bytes:
        return build_measure_request()

    def parse_status(self, frame: HsiFrame) -> dict:
        return parse_status(frame)

    def parse_measure(self, frame: HsiFrame) -> dict:
        return parse_measure(frame)
