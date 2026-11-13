"""配置驱动行为（轮询间隔继承、工程值计算）的单元测试。"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import main  # noqa: E402
from storage import ChannelBinding, engineering_value  # noqa: E402


class ResolveIntervalTest(unittest.TestCase):
    def test_cli_interval_wins(self):
        interval, source = main.resolve_interval_s(12.5, 60000)
        self.assertEqual(interval, 12.5)
        self.assertIn("--interval", source)

    def test_inherits_device_poll_interval(self):
        interval, source = main.resolve_interval_s(None, 60000)
        self.assertEqual(interval, 60.0)
        self.assertIn("poll_interval_ms", source)

    def test_falls_back_to_default(self):
        interval, source = main.resolve_interval_s(None, None)
        self.assertEqual(interval, 5.0)
        self.assertIn("默认", source)


class EngineeringValueTest(unittest.TestCase):
    def test_scale_and_offset(self):
        binding = ChannelBinding(
            sensor_code="s",
            channel_code="c",
            field="f",
            field_index=1,
            address=0x61,
            scale=2.0,
            value_offset=10.0,
        )
        self.assertEqual(engineering_value(100, binding), 210.0)

    def test_negative_and_zero(self):
        binding = ChannelBinding(
            sensor_code="s",
            channel_code="c",
            field="f",
            field_index=1,
            address=0x61,
            scale=-0.1,
            value_offset=0.0,
        )
        self.assertEqual(engineering_value(100, binding), -10.0)
        self.assertEqual(engineering_value(0, binding), 0.0)


if __name__ == "__main__":
    unittest.main()
