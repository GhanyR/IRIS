# IRIS Device Verification — 2026-08-19

## Build

- App: IRIS — In-bed Recognition & Intervention System
- Application ID: `app.nophoneinbed` (retained for upgrade continuity)
- Version: `0.1.0` (`versionCode` 1)
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- APK size: 29,857,625 bytes
- SHA-256: `b6aba3bace2532af2e70bb967454f6eaaf0f8299e78e66ae5904ea33696fd0a9`

## Automated verification

- 35 JVM unit tests passed with 0 failures and 0 errors.
- 12 Android instrumented tests passed on the physical device.
- The device tests cover the rear camera, reusable AI/OpenCV frame ownership, the reviewed object model, bright-screen and dark-phone-shape detection, oblique camera pose projection, foreground service behavior, and alarm output.
- The privacy contract confirms there is no internet permission and no frame persistence API in the app source.

Command used:

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
```

## Physical device

- Device: moto g - 2025
- Android: 16 (SDK 36)
- Connection: USB ADB
- Camera placement: side/oblique view with the whole mattress visible
- Camera permission: granted
- Notification permission: granted
- Foreground tracking service: active
- Battery during final check: 100%, AC powered
- Battery temperature during final check: 31.0 C
- Android thermal status reported by IRIS: 0 (normal)
- Alarm stream: 7/7

## Calibration and live scene

- Mattress dimensions: 1.6 m x 2.0 m
- Prohibited height: 1.4 m
- Four mattress corners saved successfully
- Pose reprojection RMS: 61.79 px
- 3D bed volume: active
- Audible test: confirmed heard by the user

One dark-screen phone was present on the mattress. The generic object model did not recognize it at this small, oblique scale, so the contrast-aware dark-phone-shape path was added and verified. In the final installed build:

- Initial state: `WATCH`
- Alarm state reached after 3 positive frames, about 0.40 seconds after the first frame
- Evidence kind: `DARK_PHONE_SHAPE`
- Detections: 1
- Confidence during final sample: approximately 0.59–0.60
- Bed-volume overlap: 100%
- Inference: approximately 103–175 ms
- Analysis rate: approximately 4–6 FPS
- State remained `ALARM` for the entire final observation while the phone remained in place

The physical remove-phone transition was not performed during the final sample because the test phone remained on the mattress. The decision-engine test verifies that an active alarm clears only after three continuous clear seconds.

## Privacy

IRIS performs inference locally, does not request internet access, and does not save camera frames. Diagnostic logs contain only state, timing, evidence kind, confidence, overlap, FPS, and thermal status.
