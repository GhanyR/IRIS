# No Phone in Bed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build, install, and physically verify an offline Android tracker that alarms when any visually observable, unmodified phone enters the calibrated 3D volume above a fully visible bed from either an overhead or side camera angle.

**Architecture:** A CameraX foreground service feeds latest-only frames to a MediaPipe EfficientDet-Lite0 phone detector and an OpenCV screen-candidate detector. Pure Kotlin tracking and state logic fuse detections with an OpenCV-projected bed volume, while a view-based activity handles calibration, live diagnostics, and an alarm test; no camera frame is persisted or network permission declared.

**Tech Stack:** Kotlin 2.2.21, Android Gradle Plugin 8.13.2, Gradle 8.14, Java 17, compile/target SDK 36, min SDK 26, CameraX 1.6.1, MediaPipe Tasks Vision 0.10.35, OpenCV Android 4.14.0, AndroidX AppCompat/ConstraintLayout/Lifecycle, XML views with ViewBinding, JUnit 4, AndroidX Test, Truth.

**Spec:** `docs/superpowers/specs/2026-08-19-no-phone-in-bed-design.md`

## Global Constraints

- Package name is `app.nophoneinbed`; app label is `No Phone in Bed`.
- The monitored phone requires no marker, sticker, overlay, companion app, Bluetooth connection, or brand-specific integration.
- The app works from an overhead, wall, or side mount when the entire bed is visible and calibration passes.
- The primary physical target is the connected Motorola moto g (2025) on Android 16.
- The manifest must not declare `android.permission.INTERNET`, media-reading, media-writing, microphone, location, contacts, or account permissions.
- Frames must be closed after analysis and must never be encoded, persisted, uploaded, or added to diagnostics.
- Detection state is exactly `CLEAR`, `WATCH`, `ALARM`, or `FAULT`.
- `ALARM` begins after 3 of 5 positive frames or 1,000 ms of persistent evidence; it clears only after 3,000 ms continuously clear.
- A confirmed in-bed track survives 10,000 ms of visual occlusion.
- Inference starts at 5 frames per second and drops stale CameraX frames.
- A camera/model/calibration failure enters `FAULT`; it must never produce a false `CLEAR`.
- The production alarm uses the system alarm stream but does not silently change the user's alarm volume.
- The model source is `https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite` and its SHA-256 must equal `0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb`.
- The mechanically secured mount and secondary tether are operational requirements outside the APK.

## File map

Build and configuration:

- `settings.gradle.kts` — plugin and dependency repositories.
- `build.gradle.kts` — root Android/Kotlin plugin versions.
- `gradle.properties` — AndroidX, JVM, and Kotlin build settings.
- `gradle/wrapper/gradle-wrapper.properties` and `gradlew*` — Gradle 8.14 wrapper.
- `app/build.gradle.kts` — Android configuration and pinned dependencies.
- `app/src/main/AndroidManifest.xml` — minimal permissions, foreground service, and launcher activity.
- `app/src/main/assets/efficientdet_lite0.tflite` — verified phone-capable detector model.
- `tools/fetch_model.sh` — reproducible model download and checksum verification.

Core domain:

- `app/src/main/java/app/nophoneinbed/domain/Geometry.kt` — normalized points, rectangles, polygons, convex hull, and intersections.
- `app/src/main/java/app/nophoneinbed/domain/Calibration.kt` — calibration value object and validation.
- `app/src/main/java/app/nophoneinbed/domain/DetectionModels.kt` — detector/track/state data contracts.
- `app/src/main/java/app/nophoneinbed/domain/PhoneTrackManager.kt` — cross-frame association and occlusion retention.
- `app/src/main/java/app/nophoneinbed/domain/DetectionDecisionEngine.kt` — deterministic state machine and hysteresis.
- `app/src/main/java/app/nophoneinbed/domain/BedVolumeModel.kt` — projected-prism silhouette and detection overlap policy.

Vision and camera:

- `app/src/main/java/app/nophoneinbed/vision/PhoneObjectDetector.kt` — detector interface.
- `app/src/main/java/app/nophoneinbed/vision/MediaPipePhoneObjectDetector.kt` — MediaPipe adapter restricted to `cell phone`.
- `app/src/main/java/app/nophoneinbed/vision/ScreenCandidateDetector.kt` — OpenCV low-light rectangle evidence.
- `app/src/main/java/app/nophoneinbed/vision/CameraPoseEstimator.kt` — camera-intrinsic fallback, planar pose, and 3D prism projection.
- `app/src/main/java/app/nophoneinbed/vision/CoordinateMapper.kt` — analysis/preview normalized mapping.
- `app/src/main/java/app/nophoneinbed/vision/CameraFrameSource.kt` — CameraX binding and latest-only analysis.

Runtime and storage:

- `app/src/main/java/app/nophoneinbed/runtime/TrackerStatusStore.kt` — process-local `StateFlow` status.
- `app/src/main/java/app/nophoneinbed/runtime/AlarmController.kt` — alarm/fault tone policy.
- `app/src/main/java/app/nophoneinbed/runtime/ThermalController.kt` — inference interval from Android thermal status.
- `app/src/main/java/app/nophoneinbed/runtime/MountMovementMonitor.kt` — gravity-vector comparison that invalidates moved calibration.
- `app/src/main/java/app/nophoneinbed/runtime/TrackerForegroundService.kt` — camera, analysis, notification, wake lock, and retries.
- `app/src/main/java/app/nophoneinbed/data/CalibrationStore.kt` — typed SharedPreferences persistence.

UI:

- `app/src/main/java/app/nophoneinbed/MainActivity.kt` — permissions and setup/calibration/tracking orchestration.
- `app/src/main/java/app/nophoneinbed/ui/TrackerOverlayView.kt` — bed volume, detections, and calibration handles.
- `app/src/main/java/app/nophoneinbed/ui/CalibrationController.kt` — four physical-corner tap workflow.
- `app/src/main/res/layout/activity_main.xml` — preview, overlay, state, controls, and diagnostics.
- `app/src/main/res/values/strings.xml`, `colors.xml`, and `themes.xml` — product copy and visual tokens.

Tests mirror each production package under `app/src/test` for pure Kotlin code and `app/src/androidTest` for OpenCV, CameraX, manifest, model, service, and UI integration.

---

### Task 1: Reproducible Android shell and privacy baseline

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/app/nophoneinbed/MainActivity.kt`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `tools/fetch_model.sh`
- Create: `app/src/main/assets/efficientdet_lite0.tflite`
- Test: `app/src/test/java/app/nophoneinbed/PrivacyContractTest.kt`

**Interfaces:**
- Consumes: Android SDK 36 and JDK 17 from `/opt/homebrew/opt/openjdk@17`.
- Produces: buildable `app` module, verified model asset, and launcher `MainActivity`.

- [ ] **Step 1: Add the failing privacy contract test**

```kotlin
class PrivacyContractTest {
    @Test fun manifest_has_no_network_or_media_permissions() {
        val xml = File("src/main/AndroidManifest.xml").readText()
        assertThat(xml).doesNotContain("android.permission.INTERNET")
        assertThat(xml).doesNotContain("READ_MEDIA")
        assertThat(xml).doesNotContain("WRITE_EXTERNAL_STORAGE")
        assertThat(xml).doesNotContain("RECORD_AUDIO")
    }

    @Test fun model_asset_has_expected_sha256() {
        val bytes = File("src/main/assets/efficientdet_lite0.tflite").readBytes()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        assertThat(hash).isEqualTo("0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb")
    }
}
```

- [ ] **Step 2: Run the test to prove the module is absent**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests app.nophoneinbed.PrivacyContractTest`

Expected: FAIL because the wrapper/module/manifest/model does not yet exist.

- [ ] **Step 3: Create the minimal project and pinned build configuration**

Use `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`, Java/Kotlin target 17, ViewBinding, package `app.nophoneinbed`, CameraX 1.6.1, MediaPipe Tasks Vision 0.10.35, OpenCV 4.14.0, lifecycle 2.9.4, appcompat 1.7.1, constraintlayout 2.2.1, core-ktx 1.17.0, coroutines 1.10.2, JUnit 4.13.2, Truth 1.4.4, AndroidX Test runner 1.7.0, and Espresso 3.7.0.

The model-fetch script must execute exactly:

```bash
#!/usr/bin/env bash
set -euo pipefail
url='https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite'
expected='0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb'
target='app/src/main/assets/efficientdet_lite0.tflite'
mkdir -p "$(dirname "$target")"
curl -fsSL "$url" -o "$target"
actual="$(shasum -a 256 "$target" | awk '{print $1}')"
test "$actual" = "$expected"
```

The manifest declares only camera, foreground-service camera, wake-lock, notifications, and vibration permissions; `MainActivity` renders `activity_main.xml` with ViewBinding.

- [ ] **Step 4: Fetch the model and run the baseline build/tests**

Run:

```bash
./tools/fetch_model.sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`, `PrivacyContractTest` has 2 passing tests, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 5: Commit the baseline**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app tools
git commit -m "build: scaffold offline Android vision tracker"
```

### Task 2: Geometry, calibration, and projected-volume contracts

**Files:**
- Create: `app/src/main/java/app/nophoneinbed/domain/Geometry.kt`
- Create: `app/src/main/java/app/nophoneinbed/domain/Calibration.kt`
- Create: `app/src/main/java/app/nophoneinbed/domain/BedVolumeModel.kt`
- Test: `app/src/test/java/app/nophoneinbed/domain/GeometryTest.kt`
- Test: `app/src/test/java/app/nophoneinbed/domain/CalibrationTest.kt`
- Test: `app/src/test/java/app/nophoneinbed/domain/BedVolumeModelTest.kt`

**Interfaces:**
- Consumes: none beyond Kotlin standard library.
- Produces: `NPoint`, `NRect`, `Polygon`, `BedCalibration`, `BedVolumeProjection`, and `BedVolumeModel.overlapRatio(NRect): Float`.

- [ ] **Step 1: Write failing geometry and calibration tests**

```kotlin
@Test fun rejects_self_crossing_bed_corners() {
    val result = BedCalibration.create(
        widthMeters = 1.6f,
        lengthMeters = 2.0f,
        heightMeters = 1.4f,
        corners = listOf(NPoint(.1f,.1f), NPoint(.9f,.9f), NPoint(.9f,.1f), NPoint(.1f,.9f)),
        gravity = floatArrayOf(0f, 1f, 0f)
    )
    assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
}

@Test fun phone_box_intersecting_projected_prism_is_inside() {
    val volume = BedVolumeModel(
        BedVolumeProjection(Polygon.rectangle(.2f, .2f, .8f, .85f), reprojectionErrorPx = 1.2f)
    )
    assertThat(volume.overlapRatio(NRect(.45f, .40f, .55f, .60f))).isGreaterThan(.9f)
    assertThat(volume.overlapRatio(NRect(.82f, .40f, .92f, .60f))).isEqualTo(0f)
}
```

- [ ] **Step 2: Run the domain tests and confirm missing types**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests 'app.nophoneinbed.domain.*'`

Expected: FAIL with unresolved `BedCalibration`, `NPoint`, `NRect`, and `BedVolumeModel`.

- [ ] **Step 3: Implement normalized geometry and validation**

Implement immutable data classes with finite `[0,1]` validation, shoelace polygon area, segment-crossing rejection, Sutherland–Hodgman rectangle/polygon clipping, monotonic-chain convex hull, and the contract:

```kotlin
data class BedCalibration(
    val widthMeters: Float,
    val lengthMeters: Float,
    val heightMeters: Float,
    val mattressCorners: List<NPoint>, // head-left, head-right, foot-right, foot-left
    val gravity: List<Float>,
    val manualUpperOffset: NPoint = NPoint(0f, 0f)
)

data class BedVolumeProjection(val silhouette: Polygon, val reprojectionErrorPx: Float)

class BedVolumeModel(private val projection: BedVolumeProjection) {
    fun overlapRatio(rect: NRect): Float =
        projection.silhouette.intersectionArea(rect) / rect.area
    fun containsCenter(rect: NRect): Boolean = projection.silhouette.contains(rect.center)
}
```

Require bed dimensions `0.5..3.0 m`, prohibited height `0.2..2.5 m`, four unique corners, absolute polygon area at least `0.03`, no crossed edges, and three gravity values.

- [ ] **Step 4: Run all domain geometry tests**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests 'app.nophoneinbed.domain.*'`

Expected: all geometry/calibration/volume tests PASS.

- [ ] **Step 5: Commit domain geometry**

```bash
git add app/src/main/java/app/nophoneinbed/domain app/src/test/java/app/nophoneinbed/domain
git commit -m "feat: add calibrated bed volume geometry"
```

### Task 3: Phone tracking and deterministic alarm state machine

**Files:**
- Create: `app/src/main/java/app/nophoneinbed/domain/DetectionModels.kt`
- Create: `app/src/main/java/app/nophoneinbed/domain/PhoneTrackManager.kt`
- Create: `app/src/main/java/app/nophoneinbed/domain/DetectionDecisionEngine.kt`
- Test: `app/src/test/java/app/nophoneinbed/domain/PhoneTrackManagerTest.kt`
- Test: `app/src/test/java/app/nophoneinbed/domain/DetectionDecisionEngineTest.kt`

**Interfaces:**
- Consumes: `NRect`, `BedVolumeModel`.
- Produces: `PhoneEvidence`, `TrackedPhone`, `TrackerState`, `DecisionSnapshot`, `PhoneTrackManager.update`, and `DetectionDecisionEngine.update`.

- [ ] **Step 1: Write failing temporal tests**

```kotlin
@Test fun enters_alarm_after_three_of_five_positive_frames() {
    val engine = DetectionDecisionEngine(DecisionPolicy.default())
    val states = listOf(0L, 200L, 400L, 600L, 800L).mapIndexed { index, time ->
        engine.update(time, if (index in setOf(0, 2, 4)) confirmedInside() else emptyList(), fault = null).state
    }
    assertThat(states.last()).isEqualTo(TrackerState.ALARM)
}

@Test fun confirmed_phone_survives_ten_second_occlusion() {
    val tracks = PhoneTrackManager(occlusionRetentionMs = 10_000)
    tracks.update(0, listOf(confirmedEvidence()))
    assertThat(tracks.update(9_999, emptyList()).single().lastKnownInside).isTrue()
    assertThat(tracks.update(10_001, emptyList())).isEmpty()
}

@Test fun fault_never_reports_clear() {
    val engine = DetectionDecisionEngine(DecisionPolicy.default())
    assertThat(engine.update(0, emptyList(), fault = "camera unavailable").state)
        .isEqualTo(TrackerState.FAULT)
}
```

- [ ] **Step 2: Run the temporal tests and confirm failure**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests '*PhoneTrackManagerTest' --tests '*DetectionDecisionEngineTest'`

Expected: FAIL because the tracking/state types do not exist.

- [ ] **Step 3: Implement evidence, association, and hysteresis**

Use IoU `>= 0.25` or normalized center distance `<= 0.12` to associate tracks. Define:

```kotlin
enum class TrackerState { CLEAR, WATCH, ALARM, FAULT }
enum class EvidenceKind { OBJECT_MODEL, LUMINOUS_SCREEN }

data class PhoneEvidence(
    val box: NRect,
    val confidence: Float,
    val kind: EvidenceKind,
    val overlapRatio: Float,
    val timestampMs: Long
)

data class DecisionPolicy(
    val strongConfidence: Float = .35f,
    val minimumOverlap: Float = .15f,
    val positiveFramesRequired: Int = 3,
    val frameWindow: Int = 5,
    val persistentPositiveMs: Long = 1_000,
    val clearAfterMs: Long = 3_000
)
```

Object-model evidence can confirm a phone. Screen evidence alone produces `WATCH` unless it is attached to an existing confirmed track. Any non-null fault immediately returns `FAULT`. Keep the state engine side-effect free.

- [ ] **Step 4: Run the tracking/state suite**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests 'app.nophoneinbed.domain.*'`

Expected: all domain tests PASS, including 3-of-5 alarm, 3-second clear, 10-second occlusion, and fault precedence.

- [ ] **Step 5: Commit temporal logic**

```bash
git add app/src/main/java/app/nophoneinbed/domain app/src/test/java/app/nophoneinbed/domain
git commit -m "feat: add phone tracking and alarm decisions"
```

### Task 4: Camera pose and side/overhead 3D prism projection

**Files:**
- Create: `app/src/main/java/app/nophoneinbed/vision/CameraPoseEstimator.kt`
- Test: `app/src/androidTest/java/app/nophoneinbed/vision/CameraPoseEstimatorTest.kt`

**Interfaces:**
- Consumes: `BedCalibration`, image width/height, `CameraIntrinsics`.
- Produces: `CameraIntrinsics`, `PoseEstimate`, and `CameraPoseEstimator.projectBedVolume(...) : Result<BedVolumeProjection>`.

- [ ] **Step 1: Write failing OpenCV instrumented projection tests**

```kotlin
@Test fun oblique_pose_projects_valid_volume() {
    val estimator = CameraPoseEstimator()
    val result = estimator.projectBedVolume(
        calibration = sideViewCalibration(),
        intrinsics = CameraIntrinsics(950.0, 950.0, 640.0, 360.0, DoubleArray(5)),
        imageWidth = 1280,
        imageHeight = 720
    ).getOrThrow()
    assertThat(result.silhouette.points.size).isAtLeast(4)
    assertThat(result.reprojectionErrorPx).isLessThan(5f)
    assertThat(result.silhouette.points.all { it.x in 0f..1f && it.y in 0f..1f }).isTrue()
}

@Test fun rejects_pose_with_large_reprojection_error() {
    assertThat(estimator.projectBedVolume(inconsistentCalibration(), intrinsics(), 1280, 720).isFailure).isTrue()
}
```

- [ ] **Step 2: Run on the connected Android device and confirm failure**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.nophoneinbed.vision.CameraPoseEstimatorTest`

Expected: FAIL because `CameraPoseEstimator` is missing.

- [ ] **Step 3: Implement planar pose and prism projection**

Initialize OpenCV, construct mattress object points in meters as `(0,0,0)`, `(width,0,0)`, `(width,length,0)`, `(0,length,0)`, solve with `Calib3d.solvePnP(..., SOLVEPNP_IPPE)`, and calculate RMS reprojection error. Project both possible vertical directions; select the sign whose top-face center has smaller positive camera depth than the mattress center. Reject non-positive depth, RMS error above 5 px, non-finite output, and a silhouette leaving normalized bounds by more than 0.05.

Construct the final forbidden silhouette as the convex hull of the projected four mattress vertices and four top vertices, then apply `manualUpperOffset` in normalized preview coordinates.

Use camera metadata in priority order:

```kotlin
fun fromCharacteristics(c: CameraCharacteristics, width: Int, height: Int): CameraIntrinsics {
    val calibrated = c[CameraCharacteristics.LENS_INTRINSIC_CALIBRATION]
    if (calibrated != null) return CameraIntrinsics(
        calibrated[0].toDouble(), calibrated[1].toDouble(),
        calibrated[2].toDouble(), calibrated[3].toDouble(), DoubleArray(5)
    )
    val focalMm = c[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]!!.first()
    val sensor = c[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE]!!
    return CameraIntrinsics(
        focalMm / sensor.width * width, focalMm / sensor.height * height,
        width / 2.0, height / 2.0, DoubleArray(5)
    )
}
```

- [ ] **Step 4: Run unit and projection instrumentation suites**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.nophoneinbed.vision.CameraPoseEstimatorTest
```

Expected: all tests PASS on overhead and oblique fixtures.

- [ ] **Step 5: Commit pose estimation**

```bash
git add app/src/main/java/app/nophoneinbed/vision/CameraPoseEstimator.kt app/src/androidTest/java/app/nophoneinbed/vision/CameraPoseEstimatorTest.kt
git commit -m "feat: project bed volume for side and overhead views"
```

### Task 5: Generic phone and luminous-screen detectors

**Files:**
- Create: `app/src/main/java/app/nophoneinbed/vision/PhoneObjectDetector.kt`
- Create: `app/src/main/java/app/nophoneinbed/vision/MediaPipePhoneObjectDetector.kt`
- Create: `app/src/main/java/app/nophoneinbed/vision/ScreenCandidateDetector.kt`
- Test: `app/src/androidTest/java/app/nophoneinbed/vision/MediaPipePhoneObjectDetectorTest.kt`
- Test: `app/src/androidTest/java/app/nophoneinbed/vision/ScreenCandidateDetectorTest.kt`

**Interfaces:**
- Consumes: `Bitmap`, timestamp, and optional bed crop polygon.
- Produces: `PhoneObjectDetector.detect(Bitmap, Long): List<PhoneEvidence>` and `ScreenCandidateDetector.detect(Mat, Long): List<PhoneEvidence>`.

- [ ] **Step 1: Add failing model/rectangle instrumentation tests**

```kotlin
@Test fun model_loads_and_exposes_cell_phone_category() {
    MediaPipePhoneObjectDetector(context).use { detector ->
        assertThat(detector.categoryAllowlist).containsExactly("cell phone")
    }
}

@Test fun bright_phone_shaped_rectangle_is_watch_evidence() {
    val frame = Mat.zeros(480, 640, CvType.CV_8UC3)
    Imgproc.rectangle(frame, Point(250.0, 130.0), Point(350.0, 330.0), Scalar(255.0,255.0,255.0), -1)
    val results = ScreenCandidateDetector().detect(frame, 100L)
    assertThat(results).hasSize(1)
    assertThat(results.single().kind).isEqualTo(EvidenceKind.LUMINOUS_SCREEN)
}
```

- [ ] **Step 2: Run detector instrumentation and verify failure**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=app.nophoneinbed.vision`

Expected: FAIL because detector adapters are missing.

- [ ] **Step 3: Implement the MediaPipe adapter**

Create `ObjectDetectorOptions` with base model asset, running mode `IMAGE`, max results 5, score threshold 0.20, and category allowlist `listOf("cell phone")`. Convert MediaPipe bounding boxes to clamped normalized `NRect`; preserve model confidence and mark kind `OBJECT_MODEL`. Always release the detector in `close()`.

- [ ] **Step 4: Implement the low-light screen candidate adapter**

Convert the bed crop to HSV, threshold value at `max(160, percentile95)`, close morphology with a 5×5 kernel, find external contours, polygon-approximate, and retain convex quadrilaterals with image-area ratio `0.0003..0.15`, long/short aspect ratio `1.25..2.6`, and rectangular fill ratio at least `0.70`. Return confidence capped below the strong model threshold so a new rectangle alone remains `WATCH`.

- [ ] **Step 5: Run detector instrumentation and full unit tests**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=app.nophoneinbed.vision
```

Expected: model loads, allowlist is exactly `cell phone`, synthetic rectangle is found, square/very-thin controls are rejected, and all tests PASS.

- [ ] **Step 6: Commit vision detectors**

```bash
git add app/src/main/java/app/nophoneinbed/vision app/src/androidTest/java/app/nophoneinbed/vision app/src/main/assets
git commit -m "feat: detect generic phones and luminous screens"
```

### Task 6: CameraX latest-frame pipeline and calibration overlay

**Files:**
- Create: `app/src/main/java/app/nophoneinbed/vision/CoordinateMapper.kt`
- Create: `app/src/main/java/app/nophoneinbed/vision/CameraFrameSource.kt`
- Create: `app/src/main/java/app/nophoneinbed/ui/TrackerOverlayView.kt`
- Create: `app/src/main/java/app/nophoneinbed/ui/CalibrationController.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Test: `app/src/test/java/app/nophoneinbed/vision/CoordinateMapperTest.kt`
- Test: `app/src/test/java/app/nophoneinbed/ui/CalibrationControllerTest.kt`
- Test: `app/src/androidTest/java/app/nophoneinbed/vision/CameraFrameSourceTest.kt`

**Interfaces:**
- Consumes: CameraX lifecycle owner, preview surface, calibration and projected volume.
- Produces: `CameraFrameSource.start(...)`, `CameraFrameSource.stop()`, `CoordinateMapper`, `CalibrationController.onTap`, and overlay render state.

- [ ] **Step 1: Write failing rotation/crop and corner-order tests**

```kotlin
@Test fun maps_rotated_analysis_box_to_preview() {
    val mapper = CoordinateMapper(Size(1280, 720), Size(720, 1604), rotationDegrees = 90)
    assertThat(mapper.toPreview(NPoint(0f, 0f))).isEqualTo(NPoint(1f, 0f))
}

@Test fun calibration_uses_physical_corner_order() {
    val controller = CalibrationController()
    assertThat(controller.nextPrompt).isEqualTo(Corner.HEAD_LEFT)
    controller.onTap(NPoint(.1f,.2f))
    assertThat(controller.nextPrompt).isEqualTo(Corner.HEAD_RIGHT)
}
```

- [ ] **Step 2: Run mapping/calibration tests and confirm failure**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests '*CoordinateMapperTest' --tests '*CalibrationControllerTest'`

Expected: FAIL because mapper/controller types are absent.

- [ ] **Step 3: Implement coordinate mapping and calibration controller**

Map CameraX crop rect, rotation, mirror flag, and PreviewView scale type explicitly. The calibration controller must accept exactly four taps in physical order, support undo/reset, and call `BedCalibration.create` only after dimensions, height, gravity, and all corners are valid.

- [ ] **Step 4: Implement CameraX source and overlay**

Bind rear `Preview` plus `ImageAnalysis` at a target 1280×720 resolution, set `STRATEGY_KEEP_ONLY_LATEST`, use a single analysis executor, and enforce `try/finally { imageProxy.close() }`. Throttle delivery using a monotonic timestamp and mutable interval initially set to 200 ms. `TrackerOverlayView` draws only normalized geometry/detection/state supplied to it and never retains a frame bitmap.

- [ ] **Step 5: Run unit and CameraX instrumentation tests**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.nophoneinbed.vision.CameraFrameSourceTest
```

Expected: mapping/corner tests PASS; CameraX opens rear camera, delivers frames, closes proxies, and unbinds without leak/error.

- [ ] **Step 6: Commit camera and calibration UI**

```bash
git add app/src/main/java/app/nophoneinbed/vision app/src/main/java/app/nophoneinbed/ui app/src/main/res/layout app/src/test app/src/androidTest
git commit -m "feat: add camera pipeline and bed calibration overlay"
```

### Task 7: Alarm, thermal policy, status store, and foreground service

**Files:**
- Create: `app/src/main/java/app/nophoneinbed/runtime/TrackerStatusStore.kt`
- Create: `app/src/main/java/app/nophoneinbed/runtime/AlarmController.kt`
- Create: `app/src/main/java/app/nophoneinbed/runtime/ThermalController.kt`
- Create: `app/src/main/java/app/nophoneinbed/runtime/MountMovementMonitor.kt`
- Create: `app/src/main/java/app/nophoneinbed/runtime/TrackerForegroundService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/app/nophoneinbed/runtime/AlarmControllerTest.kt`
- Test: `app/src/test/java/app/nophoneinbed/runtime/ThermalControllerTest.kt`
- Test: `app/src/test/java/app/nophoneinbed/runtime/MountMovementMonitorTest.kt`
- Test: `app/src/androidTest/java/app/nophoneinbed/runtime/TrackerForegroundServiceTest.kt`

**Interfaces:**
- Consumes: calibration, camera frames, detectors, track manager, decision engine.
- Produces: `TrackerStatusStore.status: StateFlow<TrackerSnapshot>`, service Start/Stop actions, alarm/fault tones, foreground notification.

- [ ] **Step 1: Write failing alarm/thermal/service tests**

```kotlin
@Test fun alarm_start_and_stop_are_idempotent() {
    val output = FakeToneOutput()
    val alarm = AlarmController(output)
    alarm.apply(TrackerState.ALARM); alarm.apply(TrackerState.ALARM)
    assertThat(output.loopStarts).isEqualTo(1)
    alarm.apply(TrackerState.CLEAR); alarm.apply(TrackerState.CLEAR)
    assertThat(output.stops).isEqualTo(1)
}

@Test fun severe_thermal_status_slows_analysis() {
    assertThat(ThermalController.intervalMs(PowerManager.THERMAL_STATUS_SEVERE)).isEqualTo(1000L)
    assertThat(ThermalController.intervalMs(PowerManager.THERMAL_STATUS_NONE)).isEqualTo(200L)
}

@Test fun mount_rotation_above_three_degrees_invalidates_calibration() {
    val monitor = MountMovementMonitor(floatArrayOf(0f, 1f, 0f), maximumAngleDegrees = 3f)
    assertThat(monitor.isMoved(floatArrayOf(0f, .9986f, .0523f))).isFalse()
    assertThat(monitor.isMoved(floatArrayOf(0f, .9962f, .0872f))).isTrue()
}
```

- [ ] **Step 2: Run runtime tests and verify failure**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests 'app.nophoneinbed.runtime.*'`

Expected: FAIL because runtime types are missing.

- [ ] **Step 3: Implement status, alarm, and thermal components**

`TrackerStatusStore` exposes immutable snapshots using this exact contract:

```kotlin
data class TrackerSnapshot(
    val state: TrackerState,
    val detections: List<PhoneEvidence>,
    val projectedVolume: BedVolumeProjection?,
    val inferenceMs: Long,
    val analysisFps: Float,
    val thermalStatus: Int,
    val faultReason: String?
)
```

`AlarmController` wraps a `ToneOutput`; production output uses `ToneGenerator(AudioManager.STREAM_ALARM, 100)`, repeats an alarm tone while `ALARM`, plays a distinct short tone at most every 30 seconds in `FAULT`, and releases resources. `ThermalController` maps none/light/moderate to 200/300/500 ms, severe to 1,000 ms, and critical/emergency/shutdown to a fault reason instead of analysis. `MountMovementMonitor` normalizes the calibrated/current gravity vectors, computes `acos(clampedDot)` in degrees, and returns moved above 3 degrees.

- [ ] **Step 4: Implement foreground service orchestration**

Declare `android:foregroundServiceType="camera"`; create a low-importance persistent status channel; call `startForeground` before opening the camera; acquire a non-reference-counted partial wake lock while tracking; load calibration/model; register the accelerometer-backed `MountMovementMonitor`; start CameraX; run model and screen detector sequentially on the latest frame; fuse results; update notification/status/alarm; retry camera binding at 1, 2, and 4 seconds before staying in `FAULT`; enter `FAULT` when the mount rotates more than 3 degrees; release sensor listener, camera, detector, alarm, executor, and wake lock on Stop/destroy.

- [ ] **Step 5: Run runtime and service tests**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.nophoneinbed.runtime.TrackerForegroundServiceTest
```

Expected: alarm idempotency, 30-second fault cadence, thermal mapping, service foreground notification, Stop cleanup, and missing-calibration `FAULT` all PASS.

- [ ] **Step 6: Commit runtime service**

```bash
git add app/src/main/java/app/nophoneinbed/runtime app/src/main/AndroidManifest.xml app/src/test app/src/androidTest
git commit -m "feat: run tracking as an alarm foreground service"
```

### Task 8: Persistent calibration and complete activity workflow

**Files:**
- Create: `app/src/main/java/app/nophoneinbed/data/CalibrationStore.kt`
- Modify: `app/src/main/java/app/nophoneinbed/MainActivity.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Test: `app/src/test/java/app/nophoneinbed/data/CalibrationStoreTest.kt`
- Test: `app/src/androidTest/java/app/nophoneinbed/MainActivityTest.kt`
- Test: `app/src/androidTest/java/app/nophoneinbed/ManifestPrivacyTest.kt`

**Interfaces:**
- Consumes: all domain, vision, runtime, and UI contracts.
- Produces: complete Setup → Calibrate → Ready → Tracking product flow.

- [ ] **Step 1: Write failing persistence/UI/privacy tests**

```kotlin
@Test fun calibration_round_trips_without_precision_loss() {
    val original = validCalibration()
    store.save(original)
    assertThat(store.load()).isEqualTo(original)
}

@Test fun launch_without_calibration_shows_calibrate_action() {
    ActivityScenario.launch(MainActivity::class.java).use {
        onView(withId(R.id.calibrateButton)).check(matches(isDisplayed()))
        onView(withId(R.id.startButton)).check(matches(not(isEnabled())))
    }
}

@Test fun installed_manifest_has_no_internet_permission() {
    val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions?.toList().orEmpty()
    assertThat(requested).doesNotContain(Manifest.permission.INTERNET)
}
```

- [ ] **Step 2: Run persistence/UI tests and confirm failure**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

Expected: FAIL because calibration persistence and full UI states are absent.

- [ ] **Step 3: Implement typed calibration persistence**

Serialize version `1`, dimensions, height, four normalized corners, three gravity values, manual upper offset, and camera ID into private SharedPreferences. Reject unknown versions, missing/non-finite fields, invalid `BedCalibration.create`, or a different camera ID; return a typed error that drives `FAULT`/recalibration.

- [ ] **Step 4: Implement complete activity flow**

Request camera and Android 13+ notification permission in context. Display alarm-volume status using `AudioManager.getStreamVolume(STREAM_ALARM)`. Provide exact actions: Calibrate, Undo corner, Reset, Save, Test alarm (2 seconds), Start tracking, Stop tracking, and Open battery-optimization settings. Observe `TrackerStatusStore.status` with lifecycle-aware collection and update state label, colors, diagnostics, and overlay. Disable Start until calibration is valid and audible-test acknowledgment is true.

- [ ] **Step 5: Run the full automated suite and assemble APK**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew clean :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
apkanalyzer manifest permissions app/build/outputs/apk/debug/app-debug.apk
```

Expected: all tests PASS; APK builds; permission output contains camera, foreground-service camera, wake lock, notifications, and vibration only, with no Internet/media/microphone/location permission.

- [ ] **Step 6: Commit complete application flow**

```bash
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: complete tracker setup calibration and controls"
```

### Task 9: Install, calibrate, benchmark, tune, and prove the real bed outcome

**Files:**
- Create: `docs/device-verification-2026-08-19.md`
- Modify: domain/vision thresholds only when supported by recorded device evidence.

**Interfaces:**
- Consumes: completed debug APK and connected Motorola.
- Produces: installed usable app, tuned thresholds, physical acceptance evidence, and final APK path.

- [ ] **Step 1: Re-establish and record device identity**

Run:

```bash
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell dumpsys battery | sed -n '1,25p'
```

Expected: one authorized `moto g - 2025` in state `device`, Android 16, with battery/charging details recorded. If ADB is absent but USB hardware is present, unlock the Motorola, toggle USB debugging off/on, reconnect the data cable, and approve the RSA dialog before continuing.

- [ ] **Step 2: Install cleanly and grant only declared runtime permissions**

Run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant app.nophoneinbed android.permission.CAMERA
adb shell pm grant app.nophoneinbed android.permission.POST_NOTIFICATIONS
adb shell am start -n app.nophoneinbed/.MainActivity
```

Expected: install succeeds, activity is foreground, preview opens, and no unexpected permission prompt appears.

- [ ] **Step 3: Inspect the current side-view frame and calibrate the entire visible bed**

Capture one temporary development screenshot only after the user has explicitly pointed the tracker at the bed. Verify the four physical mattress corners are visible; enter measured bed dimensions; tap head-left, head-right, foot-right, foot-left; set prohibited height to 1.4 m; save; then delete the temporary screenshot after recording only non-image calibration coordinates and reprojection error.

Expected: calibration passes with reprojection RMS under 5 px and projected volume contains the whole intended airspace without covering an obviously unrelated large region.

- [ ] **Step 4: Confirm the production alarm audibly**

Tap `Test alarm`; have the user confirm a two-second sound is audible from the mounted position. Record system alarm index/max, not microphone audio.

Expected: user confirms audible sound; Start becomes enabled.

- [ ] **Step 5: Establish phone-absent baseline**

Remove every phone from the projected bed volume, start tracking, and observe state/logcat for 60 seconds.

Run: `adb logcat -c && adb logcat -v time NoPhoneInBed:I '*:S'`

Expected: `CLEAR` for 60 seconds, model continues analyzing near 5 FPS, no alarm, no frame path/base64/image log, no camera error.

- [ ] **Step 6: Prove current unmodified phone detection and clearing**

Place the current phone in the bed center without any marker or companion app. Test face-up bright screen, face-up dark screen, face-down, portrait, and landscape. Then move it fully outside a long edge and a short edge.

Expected: each visually observable in-bed case reaches `ALARM` within 2 seconds; each outside case clears within 3–5 seconds; evidence includes state timestamps, confidence, overlap, and latency only.

- [ ] **Step 7: Tune from a fixed placement matrix**

Test 12 positions: center; four mattress corners; midpoints of four edges; 20 cm above center; 60 cm above center; outside beside the most ambiguous side-view edge. Record detections/misses/false alarms. Change one threshold per run, rerun the full matrix, and retain a change only if it reduces misses without creating a persistent outside false alarm.

Expected: all clearly visible inside placements alarm; the outside control remains clear. If the outside control is collinear with the projected volume and cannot be separated, document the physical single-camera ambiguity and adjust camera position before changing model truth labels.

- [ ] **Step 8: Verify brief occlusion and night-use behavior**

After alarm begins, cover the phone with a hand for 5 seconds and confirm alarm persists. Under the actual night lighting, use the phone with its screen on and confirm the model or screen fallback reaches alarm. Do not claim dark-screen detection in total darkness unless freshly observed.

Expected: 5-second hand cover does not clear; active screen use in actual night lighting alarms.

- [ ] **Step 9: Run movement fault and 30-minute soak**

Slightly rotate the tracker after calibration and confirm `FAULT`; restore and recalibrate. Run 30 minutes while collecting process memory, thermal status, inference interval, camera errors, and service state every 5 minutes.

Expected: movement invalidates calibration; after recalibration the service survives 30 minutes, no camera leak/death occurs, no critical thermal state is hidden, and analysis never reports false `CLEAR` while stopped.

- [ ] **Step 10: Write verification report, run final suite, and commit**

`docs/device-verification-2026-08-19.md` records device/build identifiers, calibration numbers, test matrix results, audible confirmation, soak samples, known physical limits, final APK SHA-256, and any unverified lighting/occlusion state. It contains no image or personal scene description beyond “bed” and test phone position.

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew clean :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
shasum -a 256 app/build/outputs/apk/debug/app-debug.apk
git diff --check
git status --short
```

Expected: all tests/builds PASS, APK hash is recorded, diff check is clean, and only intended report/threshold changes remain.

Commit:

```bash
git add app docs/device-verification-2026-08-19.md
git commit -m "test: verify markerless tracking on the real bed"
```
