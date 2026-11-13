"""HYDAC HSI 协议帧编解码。

帧格式::

    STX(0x02) | Length | Address | Number | Data... | Checksum | ETX(0x03)

- ``Length`` = ``Address`` + ``Number`` + ``Data`` 的字节数；
- ``Checksum`` = ``(255 - ((Length + Address + Number + sum(Data)) & 0xFF)) & 0xFF``；
- 请求帧的 ``Address`` 为目标传感器地址（CS1000=0x61、HLB1400=0x63）；
- 应答帧的 ``Address`` 固定为 0x2A，``Number`` 与请求的功能码一致。

本模块只做纯字节处理，不依赖串口。
"""

from dataclasses import dataclass

STX = 0x02
ETX = 0x03
RESPONSE_ADDRESS = 0x2A

MIN_LENGTH = 0x02  # Address + Number，Data 可以为空
MAX_LENGTH = 0xFF
MAX_DATA_LENGTH = MAX_LENGTH - MIN_LENGTH


class FrameError(ValueError):
    """帧结构、校验和或应答字段不合法。"""


@dataclass(frozen=True)
class HsiFrame:
    """解析后的 HSI 帧。``raw`` 保留完整原始字节，便于日志记录。"""

    length: int
    address: int
    number: int
    data: bytes
    checksum: int
    raw: bytes


def checksum(length: int, address: int, number: int, data: bytes) -> int:
    """按 HSI 规则计算校验和。"""
    total = length + address + number + sum(data)
    return (255 - (total & 0xFF)) & 0xFF


def frame_size(length: int) -> int:
    """由帧内 Length 字段推算整帧字节数（STX/Length 占 2 字节，Checksum/ETX 占 2 字节）。"""
    if not MIN_LENGTH <= length <= MAX_LENGTH:
        raise FrameError(f"Length 超出范围: 0x{length:02X}")
    return length + 4


def build_frame(address: int, number: int, data: bytes = b"") -> bytes:
    """编码一个 HSI 帧（请求或用于测试的应答）。"""
    data = bytes(data)
    if not 0 <= address <= 0xFF:
        raise FrameError(f"Address 超出范围: {address}")
    if not 0 <= number <= 0xFF:
        raise FrameError(f"Number 超出范围: {number}")
    if len(data) > MAX_DATA_LENGTH:
        raise FrameError(f"Data 过长: {len(data)} 字节")
    length = MIN_LENGTH + len(data)
    return (
        bytes((STX, length, address, number))
        + data
        + bytes((checksum(length, address, number, data), ETX))
    )


def parse_frame(raw: bytes) -> HsiFrame:
    """解析并校验一个完整 HSI 帧。"""
    raw = bytes(raw)
    if len(raw) < MIN_LENGTH + 4:
        raise FrameError(f"帧过短: {len(raw)} 字节")
    if raw[0] != STX:
        raise FrameError(f"帧起始符错误: 0x{raw[0]:02X}")
    length = raw[1]
    if not MIN_LENGTH <= length <= MAX_LENGTH:
        raise FrameError(f"Length 超出范围: 0x{length:02X}")
    if len(raw) != frame_size(length):
        raise FrameError(
            f"帧长度不匹配: Length=0x{length:02X} 需要 {frame_size(length)} 字节，实际 {len(raw)} 字节"
        )
    if raw[-1] != ETX:
        raise FrameError(f"帧结束符错误: 0x{raw[-1]:02X}")

    address = raw[2]
    number = raw[3]
    data = raw[4:-2]
    received = raw[-2]
    expected = checksum(length, address, number, data)
    if received != expected:
        raise FrameError(
            f"校验和错误: 收到 0x{received:02X}，期望 0x{expected:02X}"
        )
    return HsiFrame(
        length=length,
        address=address,
        number=number,
        data=data,
        checksum=received,
        raw=raw,
    )


def validate_response(frame: HsiFrame, expected_number: int) -> None:
    """校验应答帧的固定地址与功能码。"""
    if frame.address != RESPONSE_ADDRESS:
        raise FrameError(
            f"应答 Address 错误: 0x{frame.address:02X}，期望 0x{RESPONSE_ADDRESS:02X}"
        )
    if frame.number != expected_number:
        raise FrameError(
            f"应答 Number 错误: 0x{frame.number:02X}，期望 0x{expected_number:02X}"
        )
