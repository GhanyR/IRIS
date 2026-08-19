# IRIS

**In-bed Recognition & Intervention System** is an Android vision app that watches a calibrated bed area and sounds an alarm when a phone is persistently detected inside it.

IRIS is designed to work from a ceiling or side-mounted Android phone, as long as the full mattress is visible. It does not require markers, stickers, or modifications to the phone being detected.

## How it works

- Four-corner bed calibration defines the mattress and the prohibited volume above it.
- An on-device object detector recognizes the standard `cell phone` class.
- A contrast-aware shape detector confirms small, dark phones that the generic model can miss at oblique angles.
- A local OpenCV fallback looks for phone-like illuminated screens.
- Temporal tracking filters brief false detections and retains short occlusions.
- A foreground service keeps monitoring active and sounds the Android alarm stream.
- Mount movement, camera failure, thermal limits, and invalid calibration fail visibly instead of silently.

All camera analysis runs locally. IRIS does not save or upload camera frames.

## Device setup

1. Mount the monitoring phone so the full mattress is visible.
2. Open IRIS and grant camera and notification permissions.
3. Enter bed width, length, and prohibited height.
4. Tap **Kalibrasi**, then tap head-left, head-right, foot-right, and foot-left.
5. Save the calibration, set Android alarm volume, and use **Tes bunyi 2 dtk**.
6. Tap **Mulai**.

Recalibrate whenever the monitoring phone or bed moves.

## Wireless setup from a Mac

IRIS can be viewed and controlled from a Mac over Wi-Fi with scrcpy. Bluetooth is not used because it is too slow for responsive camera preview.

The initial enablement uses USB once:

```bash
brew install scrcpy
adb tcpip 5555
adb connect 10.20.10.50:5555
./tools/iris_wireless_mirror.sh
```

After that, the cable can be removed while the Motorola and Mac remain on the same Wi-Fi. If the router gives the phone a different address, run the launcher with the new address:

```bash
IRIS_DEVICE_IP=10.20.10.50 ./tools/iris_wireless_mirror.sh
```

The launcher opens IRIS and provides live video plus mouse control for four-corner calibration. During active tracking, preview frames exist only in process memory and are generated only while the IRIS window is being viewed.

Legacy ADB TCP/IP normally needs to be enabled again after the phone reboots. Monitoring itself does not need the Mac connection.

## Build and test

Requirements: JDK 17, Android SDK 36, and an arm64 Android device.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=$HOME/Library/Android/sdk \
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a bounded runtime check after monitoring is active:

```bash
IRIS_SOAK_SECONDS=1800 ./tools/iris_soak_monitor.sh
```

Keep the monitoring phone connected to power and exempt IRIS from Android battery optimization. Android does not allow IRIS to silently restart its camera foreground service directly after a reboot, so reopen IRIS after the phone restarts.

The Android application ID intentionally remains `app.nophoneinbed` so existing installations can upgrade without losing local calibration.

## Current status

IRIS is an experimental personal safety/productivity tool, not a security system. Detection quality depends on lighting, occlusion, phone size in frame, and camera placement.
