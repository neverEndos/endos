"""CS1000 解析单元测试。"""

import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from hsi import cs1000  # noqa: E402
from hsi.frame import FrameError, RESPONSE_ADDRESS, build_frame, parse_frame  # noqa: E402


def measure_frame(values, number=3, address=RESPONSE_ADDRESS):
    data = struct.pack("<10h", *values)
    raw = build_frame(address, number, data)
    return parse_frame(raw)


class Cs1000ParseTest(unittest.TestCase):
    def test_parse_all_ten_fields(self):
        frame = measure_frame([100, 200, 300, 21, 22, 23, 24, 250, 11, 55])
        parsed = cs1000.parse_measure(frame)
        fields = parsed["fields"]
        self.assertEqual(len(fields), 10)
        self.assertEqual(parsed["quality"], 0)
        self.assertEqual(parsed["format"], "0x16")
        self.assertEqual(fields["iso_4"], 10.0)
        self.assertEqual(fields["iso_6"], 20.0)
        self.assertEqual(fields["iso_14"], 30.0)
        self.assertEqual(fields["sae_a"], 2.1)
        self.assertEqual(fields["sae_b"], 2.2)
        self.assertEqual(fields["sae_c"], 2.3)
        self.assertEqual(fields["sae_d"], 2.4)
        self.assertEqual(fields["temperature_c"], 25.0)
        self.assertEqual(fields["flow_state"], 11)
        self.assertEqual(fields["drive_pct"], 55)
        self.assertEqual(parsed["raw_fields"], [100, 200, 300, 21, 22, 23, 24, 250, 11, 55])

    def test_negative_values(self):
        frame = measure_frame([-100, -1, 0, 0, 0, 0, 0, -25, 22, 0])
        fields = cs1000.parse_measure(frame)["fields"]
        self.assertEqual(fields["iso_4"], -10.0)
        self.assertEqual(fields["iso_6"], -0.1)
        self.assertEqual(fields["temperature_c"], -2.5)
        parsed = cs1000.parse_measure(frame)
        self.assertEqual(parsed["raw_fields"][0], -100)
        self.assertEqual(parsed["raw_fields"][7], -25)

    def test_invalid_flow_state_marks_quality(self):
        frame = measure_frame([0] * 8 + [12, 50])
        parsed = cs1000.parse_measure(frame)
        self.assertEqual(parsed["quality"], 1)
        self.assertEqual(parsed["fields"]["flow_state"], 12)

    def test_wrong_length_rejected(self):
        # 手工构造 0x0E 长度的应答：6 个 int16
        data = struct.pack("<6h", *([0] * 6))
        bad = parse_frame(build_frame(RESPONSE_ADDRESS, 3, data))
        self.assertEqual(bad.length, 0x0E)
        with self.assertRaises(FrameError):
            cs1000.parse_measure(bad)

    def test_wrong_function_code_rejected(self):
        frame = measure_frame([0] * 10, number=1)
        with self.assertRaises(FrameError):
            cs1000.parse_measure(frame)

    def test_status_parse(self):
        frame = parse_frame(build_frame(RESPONSE_ADDRESS, 1, b"\x00\x00\x02"))
        status = cs1000.parse_status(frame)
        self.assertEqual(status["status_code_lo"], 0)
        self.assertEqual(status["status_code_hi"], 0)
        self.assertEqual(status["status_code"], 0)
        self.assertTrue(status["valid"])
        self.assertEqual(status["raw_data_hex"], "000002")

    def test_status_nonzero_lo_invalid(self):
        frame = parse_frame(build_frame(RESPONSE_ADDRESS, 1, b"\x07\x00\x00"))
        status = cs1000.parse_status(frame)
        self.assertEqual(status["status_byte"], 7)
        self.assertEqual(status["status_code"], 7)
        self.assertFalse(status["valid"])

    def test_status_nonzero_hi_invalid(self):
        frame = parse_frame(build_frame(RESPONSE_ADDRESS, 1, b"\x00\x01\x00"))
        status = cs1000.parse_status(frame)
        self.assertEqual(status["status_code"], 0x0100)
        self.assertFalse(status["valid"])

    def test_status_wrong_length_rejected(self):
        frame = parse_frame(build_frame(RESPONSE_ADDRESS, 1, b"\x00"))
        with self.assertRaises(FrameError):
            cs1000.parse_status(frame)

    def test_driver_requests(self):
        driver = cs1000.CS1000()
        self.assertEqual(driver.address, 0x61)
        self.assertEqual(driver.build_status_request(), bytes.fromhex("020261019b03"))
        self.assertEqual(driver.build_measure_request(), bytes.fromhex("020261039903"))


if __name__ == "__main__":
    unittest.main()
