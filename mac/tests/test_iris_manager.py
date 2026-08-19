import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from iris_manager import (  # noqa: E402
    choose_serial,
    parse_adb_devices,
    parse_battery,
    parse_iris_log,
    parse_lock_state,
    parse_service_active,
)


class DeviceParsingTest(unittest.TestCase):
    def test_prefers_wireless_device_when_usb_and_wifi_are_same_phone(self):
        devices = parse_adb_devices(
            """List of devices attached
ZT4222R67C device usb:0-1 model:moto_g___2025
10.20.10.50:5555 device product:kansas model:moto_g___2025
emulator-5554 offline
"""
        )

        self.assertEqual(choose_serial(devices), "10.20.10.50:5555")

    def test_falls_back_to_usb_and_ignores_offline_devices(self):
        devices = parse_adb_devices(
            """List of devices attached
ZT4222R67C device model:moto_g___2025
10.20.10.50:5555 offline model:moto_g___2025
"""
        )

        self.assertEqual(choose_serial(devices), "ZT4222R67C")

    def test_returns_none_when_no_usable_device_exists(self):
        devices = parse_adb_devices("List of devices attached\n")

        self.assertIsNone(choose_serial(devices))


class StatusParsingTest(unittest.TestCase):
    def test_recognizes_android_compact_foreground_service_name(self):
        dump = "ServiceRecord{abc u0 app.nophoneinbed/.runtime.TrackerForegroundService c:app.nophoneinbed}"

        self.assertTrue(parse_service_active(dump))
        self.assertFalse(parse_service_active("(nothing)"))

    def test_parses_lock_state(self):
        self.assertTrue(parse_lock_state('deviceLocked=1, isActiveUnlockRunning=0'))
        self.assertFalse(parse_lock_state('deviceLocked=0, isActiveUnlockRunning=0'))
        self.assertIsNone(parse_lock_state('trustState=UNKNOWN'))

    def test_parses_battery_and_power_source(self):
        status = parse_battery(
            """AC powered: false
USB powered: true
Wireless powered: false
level: 93
temperature: 276
"""
        )

        self.assertEqual(status["level"], 93)
        self.assertEqual(status["temperature_c"], 27.6)
        self.assertEqual(status["power"], "USB")

    def test_parses_latest_runtime_state_without_retaining_frame_data(self):
        status = parse_iris_log(
            "08-19 12:30:10.000 I/IRIS: state=ALARM detections=2 confidence=0.73 inference_ms=118 fps=2.0 thermal=0"
        )

        self.assertEqual(status["state"], "ALARM")
        self.assertEqual(status["detections"], 2)
        self.assertEqual(status["confidence"], 0.73)
        self.assertNotIn("frame", status)


if __name__ == "__main__":
    unittest.main()
