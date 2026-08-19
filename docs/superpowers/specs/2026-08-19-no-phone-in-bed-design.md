# No Phone in Bed — Android Vision Tracker Design

Date: 2026-08-19
Status: Approved for implementation
Target device: Motorola moto g (2025), Android 16

## Objective

Build a dedicated, offline Android application that uses the Motorola's rear camera to watch a fixed bed area. The app must sound a local alarm when any ordinary mobile phone is visible inside or vertically above the calibrated bed boundary. The monitored phone requires no sticker, marker, companion app, Bluetooth connection, or brand-specific integration.

The first acceptance scene is the user's real bed with one unmodified phone currently on it.

## Product decisions

- Detection is markerless and applies to phones of different brands, colors, cases, orientations, and screen states when they are visually observable.
- The bed is a fixed four-point polygon calibrated from the live camera preview.
- The prohibited volume is the vertical prism above that polygon. With a fixed overhead camera, a phone whose image overlaps the polygon is treated as inside the prism; explicit 3D reconstruction is unnecessary.
- All inference runs locally. The app requests no Internet permission and never saves, uploads, or logs camera frames.
- The tracker sounds its own alarm. It does not block or modify the monitored phone.
- The application is a dedicated tracker, not a general surveillance or recording product.

## Approaches considered

### 1. Marker-only geometry

This offers highly deterministic tracking but requires a printed marker or overlay on every monitored phone. It is rejected because the user requires the system to work with unmodified phones.

### 2. Single generic phone detector

A pretrained object detector is the simplest markerless approach. It is kept as the primary signal, but using it alone would be fragile for small phones, overhead viewpoints, glare, darkness, hands, and partial occlusion.

### 3. Markerless detector ensemble — selected

The app combines a generic `cell phone` object detector, a luminous-screen/rectangular-candidate fallback, short-term motion tracking, and temporal state logic. The ensemble is more complex than one model but is the best fit for universal, unmodified phones and can be measured against the real scene before thresholds are finalized.

## System architecture

The application is split into independently testable components:

1. `CameraFrameSource` uses CameraX to provide correctly rotated analysis frames without saving them.
2. `PhoneObjectDetector` runs a quantized on-device object-detection model and returns generic phone bounding boxes with confidence values.
3. `ScreenCandidateDetector` finds bright, rectangular, phone-sized screen candidates inside the bed region when ambient light is low. It is a supporting signal and cannot independently classify arbitrary rectangles as phones without temporal or model support.
4. `PhoneTrackManager` associates detections across frames and preserves a phone's last known in-bed state through brief hand or blanket occlusion.
5. `BedGeometry` stores normalized four-corner calibration coordinates and calculates bounding-box/polygon intersection.
6. `DetectionDecisionEngine` fuses model, screen, geometry, tracking, confidence, and time into `CLEAR`, `WATCH`, `ALARM`, or `FAULT`.
7. `AlarmController` plays a looping local alarm using alarm audio attributes until the scene is reliably clear or tracking is stopped.
8. `TrackerForegroundService` owns the camera/inference lifecycle, foreground notification, partial wake lock, restart handling, and thermal throttling.
9. `TrackerActivity` provides calibration, live overlays, Start/Stop, alarm testing, diagnostics, and current state.

Dependencies flow inward through small interfaces so geometry, fusion, state transitions, and alarm policy can be unit-tested without a camera or Android UI.

## Frame and decision flow

1. CameraX supplies the newest frame and drops stale frames.
2. The frame is rotated and scaled once for inference.
3. The object detector runs at an initial target of 4–6 frames per second.
4. The screen-candidate detector runs only inside the calibrated bed crop and becomes more sensitive in low ambient light.
5. All detections are mapped back into normalized preview coordinates.
6. `BedGeometry` measures overlap with the bed polygon.
7. `PhoneTrackManager` associates the result with recent tracks.
8. `DetectionDecisionEngine` applies temporal hysteresis.
9. State changes update the overlay, foreground notification, diagnostics, and alarm.

Initial state policy:

- Enter `ALARM` when a strong phone detection overlaps the bed in at least 3 of the latest 5 analyzed frames, or persists for at least 1 second.
- Enter `WATCH` for weaker model detections, a plausible luminous screen, or a temporarily occluded in-bed track.
- Once a phone has been confirmed in bed, disappearance alone does not immediately clear it. The track remains in-bed for an initial 10-second occlusion grace period.
- Exit `ALARM` only after no confirmed in-bed track is present for 3 continuous seconds.
- Enter `FAULT` if the camera, model, calibration, or service is unavailable. `FAULT` uses a distinct periodic warning rather than pretending the bed is clear.

These are starting values. The final values are selected from device measurements with the real bed, current camera position, and multiple phone placements.

## Calibration and movement protection

Calibration shows the live camera preview and asks for four taps in order: top-left, top-right, bottom-right, bottom-left. The polygon is drawn immediately and saved as normalized coordinates. A configurable inward/outward margin supports edge tuning.

The app stores the tracker phone's gravity vector and orientation at calibration. A material device rotation invalidates calibration and enters `FAULT`. This catches a loose ceiling mount or a bumped camera. Translation without rotation cannot be detected reliably without a fixed environmental reference, so the mount must be mechanically secured.

## User experience

The main screen has four focused states:

- **Setup:** camera permission and battery-optimization guidance.
- **Calibrate:** full preview, four bed-corner handles, reset, and save.
- **Ready:** calibrated view, `Test alarm`, and `Start tracking`.
- **Tracking:** dimmed preview with bed outline, phone boxes, confidence, inference rate, temperature status, and a large `CLEAR`, `WATCH`, `ALARM`, or `FAULT` label.

The screen can be dimmed while tracking. A persistent foreground notification shows the same status and offers Stop. After a reboot, the user must open the app and start tracking from a visible screen because modern Android restricts starting camera foreground services invisibly from the background.

## Alarm behavior

- The alarm uses `USAGE_ALARM` and the device speaker.
- `Test alarm` plays the exact production alarm for two seconds so the user can confirm audibility before mounting.
- The alarm loops while state is `ALARM` and stops after the clear hysteresis succeeds or the user explicitly stops tracking.
- App code does not silently force the system alarm volume to maximum. The UI shows the current alarm volume and warns when it is too low.
- A camera/model/calibration `FAULT` produces a different short warning every 30 seconds.

## Privacy and security

- No `INTERNET` permission.
- No frame, photo, video, or thumbnail persistence.
- No analytics, account, cloud service, advertising identifier, or remote control.
- Diagnostics contain only timestamps, state transitions, confidence/latency numbers, and non-image error codes.
- Debug screenshots used during development are temporary, explicitly reviewed for the current test, and deleted after calibration evidence is recorded.

## Runtime and thermal policy

- Use the rear main camera with a moderate analysis resolution and `KEEP_ONLY_LATEST` backpressure.
- Start at 4–6 inference frames per second rather than camera frame rate.
- Prefer quantized CPU inference first; benchmark available acceleration on the Motorola before enabling GPU/NNAPI.
- Reduce inference rate when Android reports thermal stress, but never report `CLEAR` because processing stopped. If analysis cannot keep up, enter `FAULT`.
- Run as a camera foreground service with a partial wake lock and visible notification.
- Request the user to exempt the tracker from battery optimization during setup.

## Error handling

- Missing permission: block Start and lead directly to the required permission.
- Missing/invalid calibration: block Start and open calibration.
- Model load failure: enter `FAULT`, keep the error visible, and do not start a fake tracking session.
- Camera disconnect or repeated analyzer failure: retry with bounded backoff, then remain in `FAULT` with periodic warning.
- Low alarm volume: show a persistent warning and require a successful audible test acknowledgment.
- Thermal pressure: lower analysis rate and surface the condition in diagnostics.
- Mount movement: invalidate calibration and require recalibration.

## Testing strategy

### Automated tests

- Polygon containment and bounding-box intersection, including concave-invalid input rejection and edge margins.
- Preview-to-analysis coordinate mapping for all rotations and aspect-ratio crops.
- Temporal hysteresis and transitions among `CLEAR`, `WATCH`, `ALARM`, and `FAULT`.
- Track persistence through short occlusion and reliable clearing.
- Alarm start/stop idempotency and fault-warning cadence.
- Calibration serialization and movement invalidation.
- Analyzer backpressure and frame-closing behavior.

### Physical-device acceptance

1. App installs and starts on the connected moto g (2025).
2. Live preview clearly includes the entire calibrated bed.
3. `Test alarm` is audibly confirmed by the user.
4. With no phone on the bed, state remains `CLEAR` for at least 60 seconds.
5. With the current unmodified phone placed in the bed center, the app reaches `ALARM` within 2 seconds.
6. It detects portrait, landscape, face-up, face-down, dark-screen, bright-screen, and four edge/corner placements under normal room light.
7. It detects active phone use under the actual night lighting available in the room.
8. A hand covering the phone briefly does not immediately clear an existing alarm.
9. Moving the phone fully outside the polygon clears the alarm within 3–5 seconds.
10. Moving or rotating the tracker invalidates calibration and produces `FAULT`.
11. A 30-minute soak records inference latency, frame rate, memory, battery/charging state, and temperature without camera failure or service death.
12. All temporary camera screenshots are removed after verification.

## Known physical limits

A normal RGB camera cannot detect a phone that is fully hidden before observation, such as under an opaque blanket, nor can it guarantee classification in total darkness when the monitored screen is off. The design mitigates brief occlusion by retaining confirmed in-bed tracks and uses screen-light evidence during active use. It does not claim to overcome complete visual occlusion or absence of light.

The mount is safety-critical. The Motorola must use a mechanically locking mount plus a secondary tether and ventilation; adhesive alone is not acceptable above a person.

## Completion criteria

The MVP is complete only when the source, automated tests, debug APK, installed device build, real-camera framing, real-bed calibration, phone-present alarm, phone-absent clearing, audible alarm, privacy manifest, and soak evidence are all verified. Build success or simulated geometry alone is insufficient.
