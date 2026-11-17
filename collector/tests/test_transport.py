"""RS485 flags 严格/宽松错误传播测试，不访问真实串口。"""

import importlib
import os
import sys
import types
import unittest
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


class Rs485FlagsTest(unittest.TestCase):
    def load_transport_module(self):
        # Windows 开发机没有 Linux 串口模块，注入空模块即可测试 ioctl 分支。
        stubs = {
            name: types.ModuleType(name) for name in ("fcntl", "termios", "tty")
        }
        with patch.dict(sys.modules, stubs):
            module = importlib.import_module("transport")
            module = importlib.reload(module)
        return module

    def test_strict_ioctl_failure_raises_and_does_not_report_applied_flags(self):
        module = self.load_transport_module()
        serial = module.SerialRS485Transport()
        serial.fd = 123
        serial.rs485_flags = 0
        with patch.object(module.fcntl, "ioctl", create=True, side_effect=OSError("ioctl down")):
            with self.assertRaises(module.SerialTransportError):
                serial.set_rs485_flags(3, strict=True)
        self.assertIsNone(serial.rs485_flags)
        self.assertTrue(serial.rs485_unsupported)

    def test_legacy_mode_still_allows_board_auto_direction_fallback(self):
        module = self.load_transport_module()
        serial = module.SerialRS485Transport()
        serial.fd = 123
        with patch.object(module.fcntl, "ioctl", create=True, side_effect=OSError("unsupported")):
            serial.set_rs485_flags(3)
        self.assertIsNone(serial.rs485_flags)
        self.assertTrue(serial.rs485_unsupported)

    def test_successful_ioctl_records_flags(self):
        module = self.load_transport_module()
        serial = module.SerialRS485Transport()
        serial.fd = 123
        with patch.object(module.fcntl, "ioctl", create=True, return_value=0) as ioctl:
            serial.set_rs485_flags(5, strict=True)
        self.assertEqual(serial.rs485_flags, 5)
        ioctl.assert_called_once()


if __name__ == "__main__":
    unittest.main()
