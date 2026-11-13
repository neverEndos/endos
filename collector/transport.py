"""Linux 串口 + RS485 传输层（纯标准库，无 pyserial）。

使用 ``os`` / ``termios`` / ``fcntl`` / ``select``：

- 保存并恢复打开串口前的 termios 设置；
- 通过 ioctl ``TIOCSRS485``(0x542F) 打开 RS485 方向控制，进程内保持；
- 关闭时尽力恢复打开前的 RS485 配置。

本模块只能在 Linux（带 termios/fcntl）上导入；单元测试不依赖它。
"""

import fcntl
import os
import select
import struct
import termios
import time
import tty

from hsi.frame import STX, parse_frame

TIOCSRS485 = 0x542F
TIOCGRS485 = 0x542E
SERIAL_RS485_STRUCT_SIZE = 32  # 8 个 uint32
SERIAL_RS485_ENABLED = 1


class SerialTransportError(RuntimeError):
    """串口打开、读写或 RS485 配置失败。"""


class SerialTimeoutError(SerialTransportError):
    """等待应答超时。"""


class SerialRS485Transport:
    """9600 8N1、无流控的 RS485 串口。"""

    def __init__(
        self,
        port: str = "/dev/ttyS4",
        baud: int = 9600,
        logger=None,
    ) -> None:
        if baud != 9600:
            raise ValueError("HSI 传感器固定使用 9600 波特率")
        self.port = port
        self.baud = baud
        self.logger = logger
        self.fd: int | None = None
        self.rs485_flags: int | None = None
        self._saved_attrs: list | None = None
        self._saved_rs485: bytes | None = None

    # ------------------------------------------------------------------ 生命周期
    def open(self) -> None:
        """打开串口，设置 9600 8N1 原始模式并保存原配置。"""
        if self.fd is not None:
            raise SerialTransportError("串口已打开")
        self.fd = os.open(self.port, os.O_RDWR | os.O_NOCTTY)
        try:
            self._saved_attrs = termios.tcgetattr(self.fd)
            self._saved_rs485 = self._get_rs485_config()
            self._configure_termios()
        except Exception:
            os.close(self.fd)
            self.fd = None
            raise

    def _configure_termios(self) -> None:
        assert self.fd is not None
        tty.setraw(self.fd, termios.TCSANOW)
        attrs = termios.tcgetattr(self.fd)
        speed = getattr(termios, f"B{self.baud}")
        attrs[4] = speed  # ispeed
        attrs[5] = speed  # ospeed
        attrs[2] &= ~(
            termios.PARENB | termios.CSTOPB | getattr(termios, "CRTSCTS", 0)
        )
        attrs[2] |= termios.CS8 | termios.CREAD | termios.CLOCAL
        attrs[0] &= ~(termios.IXON | termios.IXOFF | termios.IXANY)
        # VMIN=0/VTIME=0：配合 select 使用，读操作不会无限阻塞。
        attrs[6][termios.VMIN] = 0
        attrs[6][termios.VTIME] = 0
        termios.tcsetattr(self.fd, termios.TCSANOW, attrs)

    def reopen(self) -> None:
        """关闭并重新打开串口。

        某些内核的 RS485 自动方向模式一旦卡死，只能通过重开端口恢复；探测失败
        的候选 flags 需要走这条路径。
        """
        self.close()
        self.open()

    def close(self) -> None:
        """恢复 termios/RS485 原设置并关闭 fd。"""
        if self.fd is None:
            return
        try:
            if self._saved_attrs is not None:
                try:
                    termios.tcsetattr(self.fd, termios.TCSANOW, self._saved_attrs)
                except OSError:
                    pass
            if self._saved_rs485 is not None:
                try:
                    fcntl.ioctl(self.fd, TIOCSRS485, self._saved_rs485)
                except OSError:
                    pass
        finally:
            os.close(self.fd)
            self.fd = None

    def __enter__(self) -> "SerialRS485Transport":
        self.open()
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()

    # ------------------------------------------------------------------ RS485
    def _get_rs485_config(self) -> bytes | None:
        assert self.fd is not None
        buffer = bytearray(SERIAL_RS485_STRUCT_SIZE)
        try:
            fcntl.ioctl(self.fd, TIOCGRS485, buffer, True)
            return bytes(buffer)
        except OSError:
            return None

    def set_rs485_flags(
        self, flags: int, delay_rts_before_send: int = 0, delay_rts_after_send: int = 0
    ) -> None:
        """通过 ioctl 设置 ``struct serial_rs485``（8×uint32）。"""
        if self.fd is None:
            raise SerialTransportError("串口未打开")
        payload = struct.pack(
            "8I", flags, delay_rts_before_send, delay_rts_after_send, 0, 0, 0, 0, 0
        )
        try:
            fcntl.ioctl(self.fd, TIOCSRS485, payload)
        except OSError as exc:
            raise SerialTransportError(f"设置 RS485 flags={flags} 失败: {exc}") from exc
        self.rs485_flags = flags
        if self.logger is not None:
            self.logger.info("设置 RS485 flags=%d", flags)

    # ------------------------------------------------------------------ 收发
    def transact(self, request: bytes, timeout: float = 1.0) -> object:
        """发送请求并读取一个完整应答帧。"""
        if self.fd is None:
            raise SerialTransportError("串口未打开")
        self._flush_input()
        if self.logger is not None:
            self.logger.debug("tx %s", request.hex())
        self._write_all(request)
        frame = self._read_frame(timeout)
        if self.logger is not None:
            self.logger.debug("rx %s", frame.raw.hex())
        return frame

    def _flush_input(self) -> None:
        assert self.fd is not None
        try:
            termios.tcflush(self.fd, termios.TCIFLUSH)
        except termios.error:
            pass

    def _write_all(self, data: bytes) -> None:
        assert self.fd is not None
        view = memoryview(data)
        total = 0
        deadline = time.monotonic() + 1.0
        while total < len(view):
            try:
                written = os.write(self.fd, view[total:])
                total += written
            except BlockingIOError:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise SerialTransportError("写串口超时") from None
                select.select([], [self.fd], [], remaining)
            except OSError as exc:
                raise SerialTransportError(f"写串口失败: {exc}") from exc

    def _read_frame(self, timeout: float):
        assert self.fd is not None
        deadline = time.monotonic() + timeout
        # 跳过 STX 之前可能的残留字节。
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise SerialTimeoutError(f"等待 STX 超时（{timeout:.2f} 秒）")
            ready, _, _ = select.select([self.fd], [], [], remaining)
            if not ready:
                raise SerialTimeoutError(f"等待 STX 超时（{timeout:.2f} 秒）")
            byte = os.read(self.fd, 1)
            if not byte:
                raise SerialTransportError("串口返回 EOF")
            if byte[0] == STX:
                break

        length_byte = self._read_exact(1, deadline)
        length = length_byte[0]
        if length < 0x02:
            raise SerialTransportError(f"应答 Length 非法: 0x{length:02X}")
        remainder = self._read_exact(length + 2, deadline)
        return parse_frame(bytes((STX, length)) + remainder)

    def _read_exact(self, count: int, deadline: float) -> bytes:
        assert self.fd is not None
        buffer = bytearray()
        while len(buffer) < count:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise SerialTimeoutError(
                    f"读取应答超时：需要 {count} 字节，已收到 {len(buffer)} 字节"
                )
            ready, _, _ = select.select([self.fd], [], [], remaining)
            if not ready:
                raise SerialTimeoutError(
                    f"读取应答超时：需要 {count} 字节，已收到 {len(buffer)} 字节"
                )
            try:
                chunk = os.read(self.fd, count - len(buffer))
            except BlockingIOError:
                continue
            if not chunk:
                raise SerialTransportError("串口返回 EOF")
            buffer.extend(chunk)
        return bytes(buffer)
