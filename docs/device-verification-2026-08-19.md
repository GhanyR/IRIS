# IRIS Device Verification — 2026-08-19

## Build installed

- App: IRIS — In-bed Recognition & Intervention System
- Application ID: `app.nophoneinbed` (retained for upgrade continuity)
- Version: `0.2.0` (`versionCode` 2)
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- APK size: 32,632,387 bytes
- APK SHA-256: `7d2d5d426d3ed8dba63ae888edbd0faa3d5fc94943f870c60247a1b7a01192c4`
- EfficientDet-Lite2 SHA-256: `b3f50554cb0ea559e90328845f7d9ba4d13c8bff372914d24e06bc8bb72fa896`

## Automated verification

- 49 JVM unit tests passed with 0 failures and 0 errors.
- 19 Android instrumented tests passed with 0 failures and 0 errors on the physical moto g - 2025, Android 16 / SDK 36.
- The complete device suite also passed through the Wi-Fi ADB serial; that is the same physical Motorola, not an additional device.
- Coverage includes alarm clearing from fresh evidence, 60-second capped indefinite camera retry, wake-lock leasing, publish throttling, persistent armed state, in-memory preview privacy, rear-camera frames, EfficientDet-Lite2 loading, generated angle/clutter cases, black/covered-frame faults, oblique bed-volume projection, foreground-service behavior, and the setup UI.
- `git diff --check`, both zsh launcher syntax checks, and the reviewed model checksum passed.

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

The final APK was reinstalled after the complete test suite. At the last reading, battery was 96–97% and 28.0 C. Android reported neither AC nor USB charging, so true continuous deployment still requires a working wall-power connection.

## Physical acceptance state

Earlier version 0.1.0 physically alarmed on the dark-screen phone in the user's cluttered, oblique bed scene using `DARK_PHONE_SHAPE` evidence at roughly 0.59–0.60 confidence and full bed-volume overlap. That earlier result exposed the stale-track clear-delay bug fixed in 0.2.0; it is not treated as final 0.2.0 acceptance.

The connected Android test runner correctly reset app data. Final 0.2.0 therefore needs a fresh four-corner calibration before monitoring can start. The Motorola is currently protected by its secure lock screen (`deviceLocked=1`). IRIS and the Wi-Fi scrcpy window are open, but bypassing or guessing that credential is intentionally out of scope.

Still required after the owner unlocks once:

1. Save the four visible mattress corners.
2. Confirm one phone on the cluttered mattress reaches `ALARM`.
3. Remove it and measure `CLEAR` in 3.0–4.0 seconds.
4. Replace it at another visible position and confirm `ALARM` again.
5. Run the bounded soak and record its exact duration.

No final physical 0.2.0 clear/re-detect or soak claim is made before those steps actually occur.

## Privacy

IRIS performs inference locally, does not request internet/media/storage permissions, and does not save camera frames. Active-monitoring preview JPEGs exist only in process memory while the Activity is visible. Diagnostic logs contain state, timing, evidence kind, confidence, overlap, FPS, and thermal status, never pixels.
