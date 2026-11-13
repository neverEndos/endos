"""HSI 帧编解码单元测试（不依赖串口）。"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from hsi.frame import (  # noqa: E402
    ETX,
    FrameError,
    RESPONSE_ADDRESS,
    STX,
    build_frame,
    checksum,
    frame_size,
    parse_frame,
    validate_response,
)


class ChecksumTest(unittest.TestCase):
    def test_cs1000_status_request_checksum(self):
        # 02 02 61 01 9B 03
        self.assertEqual(checksum(2, 0x61, 1, b""), 0x9B)

    def test_hlb1400_status_request_checksum(self):
        # 02 02 63 01 99 03
        self.assertEqual(checksum(2, 0x63, 1, b""), 0x99)

    def test_checksum_with_data(self):
        self.assertEqual(checksum(4, 0x2A, 3, b"\x01\x02"), 0xCB)


class FrameBuildTest(unittest.TestCase):
    def test_build_status_request(self):
        self.assertEqual(
            build_frame(0x61, 1),
            bytes((STX, 0x02, 0x61, 0x01, 0x9B, ETX)),
        )

    def test_build_hlb1400_measure_request(self):
        self.assertEqual(
            build_frame(0x63, 3),
            bytes((STX, 0x02, 0x63, 0x03, 0x97, ETX)),
        )

    def test_build_with_data(self):
        frame = build_frame(RESPONSE_ADDRESS, 3, b"\x10\x00")
        self.assertEqual(len(frame), frame_size(4))
        self.assertEqual(frame[2], RESPONSE_ADDRESS)
        self.assertEqual(frame[3], 3)
        self.assertEqual(frame[4:6], b"\x10\x00")

    def test_build_rejects_bad_inputs(self):
        with self.assertRaises(FrameError):
            build_frame(-1, 1)
        with self.assertRaises(FrameError):
            build_frame(0x61, 256)
        with self.assertRaises(FrameError):
            build_frame(0x61, 1, bytes(254))

    def test_frame_size(self):
        self.assertEqual(frame_size(0x02), 6)
        self.assertEqual(frame_size(0x16), 0x1A)
        with self.assertRaises(FrameError):
            frame_size(1)
        with self.assertRaises(FrameError):
            frame_size(256)


class FrameParseTest(unittest.TestCase):
    def test_parse_response(self):
        raw = build_frame(RESPONSE_ADDRESS, 3, bytes(range(20)))
        frame = parse_frame(raw)
        self.assertEqual(frame.length, 0x16)
        self.assertEqual(frame.address, RESPONSE_ADDRESS)
        self.assertEqual(frame.number, 3)
        self.assertEqual(frame.data, bytes(range(20)))
        self.assertEqual(frame.raw, raw)
        self.assertEqual(validate_response(frame, 3), None)

    def test_parse_rejects_bad_checksum(self):
        raw = bytearray(build_frame(RESPONSE_ADDRESS, 3, b"\x00\x01"))
        raw[-2] ^= 0xFF
        with self.assertRaises(FrameError):
            parse_frame(bytes(raw))

    def test_parse_rejects_truncated_frame(self):
        raw = build_frame(RESPONSE_ADDRESS, 3, b"\x00\x01")
        with self.assertRaises(FrameError):
            parse_frame(raw[:-1])

    def test_parse_rejects_bad_start_and_end(self):
        raw = bytearray(build_frame(RESPONSE_ADDRESS, 3, b"\x00\x01"))
        raw[0] = 0x01
        with self.assertRaises(FrameError):
            parse_frame(bytes(raw))
        raw[0] = STX
        raw[-1] = 0x04
        with self.assertRaises(FrameError):
            parse_frame(bytes(raw))

    def test_parse_rejects_length_mismatch(self):
        raw = bytearray(build_frame(RESPONSE_ADDRESS, 3, b"\x00\x01"))
        raw[1] = 0x05
        with self.assertRaises(FrameError):
            parse_frame(bytes(raw))

    def test_validate_response_rejects_sensor_address(self):
        frame = parse_frame(build_frame(0x61, 3, b"\x00\x01"))
        with self.assertRaises(FrameError):
            validate_response(frame, 3)

    def test_validate_response_rejects_wrong_number(self):
        frame = parse_frame(build_frame(RESPONSE_ADDRESS, 1, b"\x00"))
        with self.assertRaises(FrameError):
            validate_response(frame, 3)


if __name__ == "__main__":
    unittest.main()
