# IRIS 24/7 and Wireless Setup Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make IRIS clear alarms from fresh evidence correctly, self-heal during continuous monitoring, and expose setup/monitoring on the user's Mac over Wi-Fi.

**Architecture:** Keep the foreground service as camera owner, move time-dependent health decisions into pure tested policies, publish a throttled in-memory preview for the activity, and use scrcpy over ADB TCP/IP rather than adding network permission to IRIS. Detection remains three-path and is validated with deterministic generated frames plus the real cluttered bed.

**Tech Stack:** Kotlin, Android 16/SDK 36, CameraX, OpenCV, MediaPipe Tasks, Kotlin StateFlow, ADB, scrcpy, zsh.

**Spec:** `docs/superpowers/specs/2026-08-19-iris-24x7-wireless-hardening-design.md`

## Global Constraints

- Application ID remains `app.nophoneinbed` for upgrade continuity.
- No internet, media-read, storage-write, audio-recording, or location permission.
- No camera frame may be persisted or uploaded.
- Physical target is moto g - 2025, Android 16, arm64-v8a.
- IRIS reports `CLEAR` only with fresh camera frames and valid calibration.

---

### Task 1: Fresh-evidence alarm state

**Files:**
- Modify: `app/src/main/java/app/nophoneinbed/domain/DetectionModels.kt`
- Modify: `app/src/main/java/app/nophoneinbed/domain/DetectionDecisionEngine.kt`
- Test: `app/src/test/java/app/nophoneinbed/domain/DetectionDecisionEngineTest.kt`

**Interfaces:**
- Consumes: `TrackedPhone.lastSeenMs`, decision update timestamp.
- Produces: `DecisionPolicy.maximumEvidenceAgeMs: Long` and stale-track filtering.

- [x] **Step 1: Write a failing retained-track regression test**
- [x] **Step 2: Run the single test and verify it remains `ALARM` incorrectly**
- [x] **Step 3: Add a 500 ms evidence-age policy and filter alarm/watch evidence**
- [x] **Step 4: Run the complete decision-engine test class and verify green**

### Task 2: Camera health and indefinite recovery

**Files:**
- Create: `app/src/main/java/app/nophoneinbed/runtime/CameraHealthPolicy.kt`
- Create: `app/src/test/java/app/nophoneinbed/runtime/CameraHealthPolicyTest.kt`
- Modify: `app/src/main/java/app/nophoneinbed/runtime/TrackerForegroundService.kt`

**Interfaces:**
- Produces: `CameraHealthPolicy.onBound(nowMs)`, `onFrame(nowMs)`, `isStalled(nowMs)`, and `retryDelayMs(attempt)`.
- Service health check runs every two seconds and restarts after ten seconds without frames.

- [x] **Step 1: Write failing tests for initial timeout, healthy frames, and 60-second retry cap**
- [x] **Step 2: Run tests and verify the policy is missing**
- [x] **Step 3: Implement the pure policy**
- [x] **Step 4: Run policy tests**
- [x] **Step 5: Integrate one scheduled watchdog and one retry callback into the service**
- [x] **Step 6: Run service instrumentation tests**

### Task 3: Wake-lock leasing and publish throttling

**Files:**
- Create: `app/src/main/java/app/nophoneinbed/runtime/RuntimeCadencePolicy.kt`
- Create: `app/src/test/java/app/nophoneinbed/runtime/RuntimeCadencePolicyTest.kt`
- Modify: `app/src/main/java/app/nophoneinbed/runtime/TrackerForegroundService.kt`

**Interfaces:**
- Produces: six-hour renewal timing and `shouldPublish(previousState, nextState, lastPublishMs, nowMs)`.

- [x] **Step 1: Write failing cadence and state-change tests**
- [x] **Step 2: Run tests and verify failure**
- [x] **Step 3: Implement cadence policy and service scheduling**
- [x] **Step 4: Run policy and alarm tests**
- [x] **Step 5: Debounce mount movement for three seconds and verify transient bumps reset**

### Task 4: In-memory monitoring preview

**Files:**
- Create: `app/src/main/java/app/nophoneinbed/runtime/TrackerPreviewStore.kt`
- Create: `app/src/test/java/app/nophoneinbed/runtime/TrackerPreviewStoreTest.kt`
- Modify: `app/src/main/java/app/nophoneinbed/runtime/TrackerStatusStore.kt`
- Modify: `app/src/main/java/app/nophoneinbed/runtime/TrackerForegroundService.kt`
- Modify: `app/src/main/java/app/nophoneinbed/MainActivity.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`

**Interfaces:**
- Produces: `TrackerRuntime.previewStore.frames: StateFlow<ByteArray?>` and `offer(bitmap, nowMs)` capped at 500 ms.
- Activity displays decoded frames only while tracking.

- [x] **Step 1: Write failing tests for 2 FPS throttling and defensive byte-array copies**
- [x] **Step 2: Run tests and verify failure**
- [x] **Step 3: Implement JPEG preview store without file APIs**
- [x] **Step 4: Connect service producer and activity consumer**
- [x] **Step 5: Extend privacy test to reject file output APIs in preview code**
- [x] **Step 6: Run JVM and preview/device tests**

### Task 5: Wireless Mac launcher

**Files:**
- Create: `tools/iris_wireless_mirror.sh`
- Modify: `README.md`

**Interfaces:**
- Script consumes `IRIS_DEVICE_IP` when discovery is unavailable and invokes `scrcpy --serial=<ip>:5555`.

- [x] **Step 1: Verify scrcpy availability and install it if missing**
- [x] **Step 2: Enable `adb tcpip 5555`, resolve the Motorola Wi-Fi IP, and connect**
- [x] **Step 3: Add a shell launcher with strict error handling and no stored credentials**
- [x] **Step 4: Run shell syntax validation and a real TCP/IP ADB command**
- [x] **Step 5: Launch scrcpy against the Wi-Fi serial and verify the process/device**

### Task 6: Generated vision matrix

**Files:**
- Modify: `app/src/androidTest/java/app/nophoneinbed/vision/ScreenCandidateDetectorTest.kt`
- Modify: `app/src/test/java/app/nophoneinbed/domain/BedVolumeModelTest.kt`

**Interfaces:**
- Generated Mats are test-only and contain no user camera pixels.

- [x] **Step 1: Add positive cases for portrait, landscape, oblique, screen-on, and retained occlusion**
- [x] **Step 2: Add negative clutter, card, cable, pillow, and low-contrast cases**
- [x] **Step 3: Run the detector matrix on the physical Motorola**
- [x] **Step 4: Keep evidence rules unchanged when the labeled matrix passes**
- [x] **Step 5: Re-run all detector tests**

### Task 6B: Fail-visible unusable frames

**Files:**
- Create: `app/src/main/java/app/nophoneinbed/vision/FrameQualityEvaluator.kt`
- Create: `app/src/androidTest/java/app/nophoneinbed/vision/FrameQualityEvaluatorTest.kt`
- Modify: `app/src/main/java/app/nophoneinbed/runtime/TrackerForegroundService.kt`

- [x] **Step 1: Prove black and flat frames fail before production implementation exists**
- [x] **Step 2: Implement luma and scene-detail checks**
- [x] **Step 3: Integrate the check before object inference**
- [x] **Step 4: Run the three-case physical-device test class**

### Task 7: Physical verification and release

**Files:**
- Create: `tools/iris_soak_monitor.sh`
- Modify: `docs/device-verification-2026-08-19.md`
- Modify: `README.md`

**Interfaces:**
- Soak script records timestamp, PID, latest state, frame age, thermal status, battery temperature, and crashes without camera pixels.

- [x] **Step 1: Run full JVM, instrumented, and APK build tasks**
- [ ] **Step 2: Install without changing the application ID and restore calibration**
- [ ] **Step 3: Verify real phone ALARM, physical removal CLEAR, and replacement ALARM**
- [ ] **Step 4: Run and record a bounded physical soak; state the exact observed duration**
- [ ] **Step 5: Verify Wi-Fi control, privacy contract, clean Git state, and remote identity**
- [ ] **Step 6: Commit and push to `GhanyR/IRIS` as Ghany Rasyid**
