"""HLB1400 解析单元测试。"""

import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from hsi import hlb1400  # noqa: E402
from hsi.frame import FrameError, RESPONSE_ADDRESS, build_frame, parse_frame  # noqa: E402


def frame_with_values(values, number=3, address=RESPONSE_ADDRESS):
    fmt = f"<{len(values)}h"
    data = struct.pack(fmt, *values)
    return parse_frame(build_frame(address, number, data))


class Hlb1400LegacyTest(unittest.TestCase):
    def test_parse_six_fields(self):
        values = [100, 200, 300, 400, 55, 250]
        parsed = hlb1400.parse_measure(frame_with_values(values))
        self.assertEqual(parsed["format"], "0x0E")
        self.assertEqual(parsed["quality"], 0)
        fields = parsed["fields"]
        self.assertEqual(len(fields), 6)
        self.assertEqual(fields["conductivity_ns_m"], 10.0)
        self.assertEqual(fields["conductivity_change_pct"], 20.0)
        self.assertEqual(fields["dc"], 3.0)
        self.assertEqual(fields["dc_change_pct"], 40.0)
        self.assertEqual(fields["saturation_pct"], 55)
        self.assertEqual(fields["temperature_c"], 25.0)

    def test_negative_values(self):
        parsed = hlb1400.parse_measure(
            frame_with_values([-100, -200, -300, -400, 0, -250])
        )
        fields = parsed["fields"]
        self.assertEqual(fields["conductivity_ns_m"], -10.0)
        self.assertEqual(fields["conductivity_change_pct"], -20.0)
        self.assertEqual(fields["dc"], -3.0)
        self.assertEqual(fields["temperature_c"], -25.0)


class Hlb1400ExtendedTest(unittest.TestCase):
    def test_parse_eight_fields(self):
        values = [100, 200, 300, 55, 250, 111, 222, 333]
        parsed = hlb1400.parse_measure(frame_with_values(values))
        self.assertEqual(parsed["format"], "0x12")
        fields = parsed["fields"]
        self.assertEqual(len(fields), 8)
        self.assertEqual(fields["conductivity_ns_m"], 10.0)
        self.assertEqual(fields["conductivity_change_pct"], 20.0)
        self.assertEqual(fields["dc"], 3.0)
        self.assertEqual(fields["saturation_pct"], 55)
        self.assertEqual(fields["temperature_c"], 25.0)
        self.assertEqual(fields["raw_conductivity"], 111)
        self.assertEqual(fields["raw_dc"], 222)
        self.assertEqual(fields["raw_dac"], 333)


class Hlb1400RejectTest(unittest.TestCase):
    def test_unsupported_length_rejected(self):
        parsed = frame_with_values([0] * 7)
        self.assertEqual(parsed.length, 0x10)
        with self.assertRaises(FrameError):
            hlb1400.parse_measure(parsed)

    def test_wrong_function_code_rejected(self):
        with self.assertRaises(FrameError):
            hlb1400.parse_measure(frame_with_values([0] * 6, number=2))

    def test_wrong_address_rejected(self):
        with self.assertRaises(FrameError):
            hlb1400.parse_measure(frame_with_values([0] * 6, address=0x63))

    def test_driver_requests(self):
        driver = hlb1400.HLB1400()
        self.assertEqual(driver.address, 0x63)
        self.assertEqual(driver.build_status_request(), bytes.fromhex("020263019903"))
        self.assertEqual(driver.build_measure_request(), bytes.fromhex("020263039703"))


if __name__ == "__main__":
    unittest.main()
