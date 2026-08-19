# IRIS Device Verification — 2026-08-19

## Build installed

- App: IRIS — In-bed Recognition & Intervention System
- Application ID: `app.nophoneinbed` (retained for upgrade continuity)
- Version: `0.3.0` (`versionCode` 3)
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- APK size: 32,661,443 bytes
- APK SHA-256: `4abcc06c3297dfc010118828e7b1b83a3a85b9eebbc9e2def758622a31a6956a`
- EfficientDet-Lite2 SHA-256: `b3f50554cb0ea559e90328845f7d9ba4d13c8bff372914d24e06bc8bb72fa896`

## Automated verification

- 52 JVM unit tests passed with 0 failures and 0 errors.
- 7 Mac manager unit tests passed under both the user's Python 3.13 and macOS system Python 3.9.
- 1 zsh runtime parser test passed for both compact and fully qualified Android service names.
- 21 Android instrumented tests passed with 0 failures and 0 errors on the physical moto g - 2025, Android 16 / SDK 36.
- Total unique automated checks in 0.3.0: 81.
- Coverage includes alarm clearing from fresh evidence, 60-second capped indefinite camera retry, wake-lock leasing, publish throttling, persistent armed state, in-memory preview privacy, rear-camera frames, EfficientDet-Lite2 loading, generated angle/clutter cases, black/covered-frame faults, oblique bed-volume projection, foreground-service behavior, setup UI, Mac-controlled corner dragging, nearby side-angle corner taps, manager intent actions, duplicate USB/Wi-Fi selection, Android compact service-name parsing, lock/power parsing, and pixel-free runtime log parsing.
- `git diff --check`, zsh/Python syntax checks, browser rendering with no console errors, and the reviewed model checksum passed.

Final verification command:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=$HOME/Library/Android/sdk \
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
```

## Device provisioning

- Camera permission: granted
- Notification permission: granted
- Android battery-optimization whitelist: `app.nophoneinbed` added
- Alarm stream was previously verified at 7/7
- USB ADB: working
- Wi-Fi ADB: working at `10.20.10.50:5555`
- scrcpy 4.1: launched successfully against the Wi-Fi serial and displayed the device
- Wireless launcher: `tools/iris_wireless_mirror.sh`
- Runtime monitor: `tools/iris_soak_monitor.sh`
- Mac control center: `mac/IRIS Manager.command`, with a Desktop shortcut installed
- Manager server: confirmed listening only on `127.0.0.1:8765`
- Manager controls exercised on the Motorola: test alarm, calibration-gated start, stop, and Wi-Fi Live View

The final APK was reinstalled after the complete test suite. A bounded soak then passed for 616 seconds with 21/21 healthy samples: PID and foreground service remained present, runtime logs stayed 0–1 seconds fresh, battery moved from 88% to 86%, and temperature moved from 31.0 C to a stable 33.0 C. Android reported neither AC, USB, nor wireless charging, so true continuous deployment still requires a working wall-power connection.

## Physical acceptance state

Earlier version 0.1.0 physically alarmed on the dark-screen phone in the user's cluttered, oblique bed scene using `DARK_PHONE_SHAPE` evidence at roughly 0.59–0.60 confidence and full bed-volume overlap. That earlier result exposed the stale-track clear-delay bug fixed in 0.2.0; it is not treated as final 0.3.0 acceptance.

The connected Android test runner correctly reset app data. The Motorola was subsequently unlocked and IRIS 0.3.0 plus the Wi-Fi Live View were opened. After the Motorola was panned right/down, the full mattress became visible. Four ordered cyan corners were created, an existing corner was dragged from the Mac control path, and the physical projection validated and persisted. IRIS then started as an active camera foreground service and continuously reported `CLEAR` with zero detections for 15 seconds on the real empty, cluttered mattress at roughly 2.5–4.7 FPS.

Still required for final positive/transition acceptance:

1. Place one phone on the calibrated mattress and confirm `ALARM`.
2. Remove it and measure `CLEAR` in 3.0–4.0 seconds.
3. Replace it at another visible position and confirm `ALARM` again.

No final physical 0.3.0 positive clear/re-detect claim is made before those three object-transition steps actually occur. The empty-bed negative check and bounded runtime soak are complete.

After the soak completed, connecting USB power moved the mounted Motorola by more than the allowed angle for more than three seconds. IRIS correctly latched `FAULT — Posisi kamera berubah; kalibrasi ulang diperlukan` instead of continuing with stale geometry. Monitoring was intentionally stopped and disarmed; USB charging is now detected. The Mac recalibration window is open at the secure lock screen, so the owner must unlock once, adjust/save the four points, and start monitoring again.

## Privacy

IRIS performs inference locally, does not request internet/media/storage permissions, and does not save camera frames. Active-monitoring preview JPEGs exist only in process memory while the Activity is visible. Diagnostic logs contain state, timing, evidence kind, confidence, overlap, FPS, and thermal status, never pixels. IRIS Manager receives only those metadata fields; scrcpy Live View remains a separate local ADB stream.
