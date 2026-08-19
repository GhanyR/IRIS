# IRIS 24/7 and Wireless Setup Hardening Design

## Objective

IRIS must continuously evaluate a fully visible mattress from an oblique or ceiling-mounted Motorola phone, alarm when a phone is present, stop after the phone is removed, expose a live setup/monitor view on the user's Mac over Wi-Fi, and fail visibly when reliable vision is impossible.

Computer vision cannot honestly guarantee perfect classification for every physical phone, case, angle, lighting condition, and occlusion. The safety invariant is therefore: IRIS may report `CLEAR` only while camera frames are fresh, calibration is valid, the mount has not moved, and no detector has current evidence. Uncertain or unavailable vision is `WATCH` or `FAULT`, never silent `CLEAR`.

## State freshness

- `PhoneTrackManager` may retain a track for 10 seconds to preserve identity across occlusion.
- A retained track is current evidence only for 500 ms after `lastSeenMs`.
- `ALARM` clears after three continuous seconds without current in-bed evidence.
- Reappearance during the clear window immediately cancels the clear countdown.
- A stale retained track can help association when the phone reappears but cannot hold the alarm by itself.

## 24-hour runtime

- The camera foreground service remains the owner of analysis while armed.
- A 12-hour partial-wake-lock lease is renewed every six hours while the service is active, and explicitly released on stop/destruction.
- A camera health monitor treats ten seconds without a delivered frame as a fault.
- Camera open and frame-stall failures retry indefinitely with delays of 1, 2, 4, 8, 15, 30, then at most 60 seconds.
- Notification and informational logging occur on state changes or at most once per second, not every frame.
- Thermal critical/emergency/shutdown remains a visible fault; lower thermal levels slow analysis.
- A mount-angle change must remain outside the three-degree tolerance for three continuous seconds before calibration is invalidated, so a brief vibration cannot stop monitoring.
- Android 14+ does not permit a camera foreground service to be launched directly from `BOOT_COMPLETED`. IRIS therefore promises 24-hour continuous operation without reboot, not silent camera restart after reboot. `START_STICKY` still handles ordinary service process recreation when Android permits it.
- The monitoring phone is expected to remain connected to power and exempted from battery optimization.

## Wireless Mac setup

- Use official scrcpy over ADB TCP/IP on the same Wi-Fi network. Bluetooth is rejected because it is unsuitable for responsive video mirroring.
- A Mac launcher discovers an already connected TCP/IP device, otherwise uses the saved Motorola Wi-Fi address, and starts scrcpy with the IRIS app.
- The first enablement may use the current USB cable; after `adb tcpip 5555` succeeds, calibration and monitoring can be viewed and controlled without the cable until the phone reboots or Wi-Fi addressing changes.
- During setup, scrcpy mirrors the existing CameraX preview and accepts Mac mouse clicks.
- During active tracking, IRIS publishes an in-memory, downscaled JPEG preview at no more than two frames per second. `MainActivity` decodes and displays it beneath the existing detection overlay so scrcpy remains live even though the foreground service owns the camera.
- Preview bytes are process memory only. They are never written to files and never uploaded.

## Vision coverage

- Keep three independent evidence paths: accuracy-prioritized EfficientDet-Lite2 `cell phone`, luminous-screen geometry, and contrast-aware dark-phone geometry.
- Add deterministic OpenCV-generated cases for portrait, landscape, oblique quadrilateral, dark and light cases, screen-on, partial occlusion, edge-of-bed overlap, clutter, low contrast, pillow-like blobs, cables, cards, and rectangular non-phone objects.
- Positive shape evidence must be isolated, locally contrasted, within phone-scale area bounds, and intersect the calibrated 3D bed projection.
- Shape evidence remains temporal: three of five strong frames or one second persistent evidence is required for alarm.
- Physical acceptance uses the user's current cluttered bed and one real phone. Required sequence: detect and alarm, remove and clear, replace at a different visible position and alarm again.

## Diagnostics and privacy

- The UI reports state, detection count, inference time, FPS, thermal status, and retry/fault reason. Throttled diagnostics additionally log detector kind, confidence, and overlap without logging pixels.
- The service never logs or persists pixels.
- The manifest continues to remove internet and private-media permissions.
- Wireless mirroring is handled by ADB/scrcpy, outside the app's permissions.

## Acceptance criteria

1. Regression test proves a 10-second retained track does not delay the three-second clear timer.
2. Unit tests prove frame timeout, capped infinite retry, publish throttling, and wake-lock lease timing.
3. Full JVM and physical Android suites pass.
4. Wi-Fi ADB connects to the Motorola and scrcpy can launch against the TCP/IP serial.
5. With the current real phone on the cluttered mattress, IRIS reaches `ALARM`; after removal it reaches `CLEAR` in 3.0–4.0 seconds; replacement reaches `ALARM` again.
6. A physical soak shows fresh frames, active foreground service, normal/non-critical thermal status, and no `FAULT`, crash, or camera exception for the observed interval. The repository must label the exact observed duration rather than extrapolate it to 24 hours.
