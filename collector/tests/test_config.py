"""配置驱动行为（轮询间隔继承、工程值计算）的单元测试。"""

import os
import sys
import unittest
from types import SimpleNamespace

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


class ResolveTimeoutTest(unittest.TestCase):
    def test_cli_timeout_wins(self):
        timeout, source = main.resolve_timeout_s(2.5, 8000)
        self.assertEqual(timeout, 2.5)
        self.assertIn("--timeout", source)

    def test_inherits_device_timeout_ms(self):
        timeout, source = main.resolve_timeout_s(None, 2500)
        self.assertEqual(timeout, 2.5)
        self.assertIn("timeout_ms", source)

    def test_timeout_falls_back_to_default(self):
        timeout, source = main.resolve_timeout_s(None, None)
        self.assertEqual(timeout, 1.0)
        self.assertIn("默认", source)


class ResolveRetriesTest(unittest.TestCase):
    def test_cli_zero_is_an_explicit_override(self):
        retries, source = main.resolve_retries(0, 3)
        self.assertEqual(retries, 0)
        self.assertIn("--retries", source)

    def test_inherits_device_retries(self):
        retries, source = main.resolve_retries(None, 4)
        self.assertEqual(retries, 4)
        self.assertIn("collector_device.retries", source)

    def test_retries_falls_back_to_default(self):
        retries, source = main.resolve_retries(None, None)
        self.assertEqual(retries, 1)
        self.assertIn("默认", source)


class CliDefaultsTest(unittest.TestCase):
    def test_db_backed_options_are_not_fixed_by_cli_defaults(self):
        args = main.build_parser().parse_args([])
        self.assertIsNone(args.interval)
        self.assertIsNone(args.timeout)
        self.assertIsNone(args.retries)
        self.assertIsNone(args.config_check_interval)

    def test_zero_and_negative_interval_timeout_are_rejected(self):
        parser = main.build_parser()
        for option in ("--interval", "--timeout"):
            for value in ("0", "-0.1", "-1"):
                with self.subTest(option=option, value=value):
                    with self.assertRaises(SystemExit):
                        parser.parse_args([option, value])

    def test_retries_allows_zero_but_rejects_negative(self):
        parser = main.build_parser()
        self.assertEqual(parser.parse_args(["--retries", "0"]).retries, 0)
        with self.assertRaises(SystemExit):
            parser.parse_args(["--retries", "-1"])

    def test_config_check_interval_rejects_zero_and_negative(self):
        parser = main.build_parser()
        for value in ("0", "-1"):
            with self.subTest(value=value), self.assertRaises(SystemExit):
                parser.parse_args(["--config-check-interval", value])


class StructureDriverSelectionTest(unittest.TestCase):
    def setUp(self):
        self.cs = SimpleNamespace(name="CS1000")
        self.hlb = SimpleNamespace(name="HLB1400")

    def context(self, active):
        return SimpleNamespace(active_sensor_codes=tuple(active))

    def test_channel_change_does_not_add_initially_unresponsive_sensor(self):
        old = self.context(("cs1000-01", "hlb1400-01"))
        new = self.context(("cs1000-01", "hlb1400-01"))
        retained, to_probe, newly = main.partition_structure_drivers(
            old, new, [self.cs, self.hlb], {"CS1000"}
        )
        self.assertEqual(retained, [self.cs])
        self.assertEqual(to_probe, [])
        self.assertEqual(newly, set())

    def test_new_or_reenabled_sensor_is_the_only_probe_target(self):
        old = self.context(("cs1000-01",))
        new = self.context(("cs1000-01", "hlb1400-01"))
        retained, to_probe, newly = main.partition_structure_drivers(
            old, new, [self.cs, self.hlb], {"CS1000"}
        )
        self.assertEqual(retained, [self.cs])
        self.assertEqual(to_probe, [self.hlb])
        self.assertEqual(newly, {"hlb1400-01"})

    def test_empty_enabled_set_is_not_treated_as_unknown(self):
        old = self.context(("cs1000-01", "hlb1400-01"))
        new = self.context(())
        responsive = {"CS1000", "HLB1400"}
        retained, to_probe, newly = main.partition_structure_drivers(
            old, new, [], responsive
        )
        self.assertEqual((retained, to_probe), ([], []))
        self.assertEqual(newly, set())
        self.assertEqual(responsive, set())


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
