package app.nophoneinbed.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import app.nophoneinbed.MainActivity
import app.nophoneinbed.data.CalibrationStore
import app.nophoneinbed.data.TrackingStateStore
import app.nophoneinbed.domain.BedCalibration
import app.nophoneinbed.domain.BedVolumeModel
import app.nophoneinbed.domain.DecisionPolicy
import app.nophoneinbed.domain.DetectionDecisionEngine
import app.nophoneinbed.domain.PhoneEvidence
import app.nophoneinbed.domain.PhoneTrackManager
import app.nophoneinbed.domain.TrackerState
import app.nophoneinbed.vision.CameraFrame
import app.nophoneinbed.vision.CameraFrameSource
import app.nophoneinbed.vision.CameraPoseEstimator
import app.nophoneinbed.vision.FrameQualityEvaluator
import app.nophoneinbed.vision.MediaPipePhoneObjectDetector
import app.nophoneinbed.vision.PhoneObjectDetector
import app.nophoneinbed.vision.ScreenCandidateDetector
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeoutException

class TrackerForegroundService : LifecycleService(), SensorEventListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val trackManager = PhoneTrackManager(occlusionRetentionMs = 10_000L)
    private val decisionEngine = DetectionDecisionEngine(DecisionPolicy.default())
    private val frameQualityEvaluator = FrameQualityEvaluator()
    private val cameraHealth = CameraHealthPolicy(FRAME_TIMEOUT_MS)
    private val cadence = RuntimeCadencePolicy(
        publishIntervalMs = PUBLISH_INTERVAL_MS,
        wakeLockTimeoutMs = WAKE_LOCK_TIMEOUT_MS,
        wakeLockRenewalMs = WAKE_LOCK_RENEWAL_MS,
    )
    private lateinit var powerManager: PowerManager
    private lateinit var alarm: AlarmController
    private lateinit var sensorManager: SensorManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var frameSource: CameraFrameSource? = null
    private var objectDetector: PhoneObjectDetector? = null
    private var screenDetector: ScreenCandidateDetector? = null
    private var calibration: BedCalibration? = null
    private var volumeModel: BedVolumeModel? = null
    private var projectedVolume: app.nophoneinbed.domain.BedVolumeProjection? = null
    private var movementMonitor: MountMovementMonitor? = null
    private var thermalStatus = 0
    private var lastFrameAtMs = 0L
    private var retryAttempt = 0
    private var retryScheduled = false
    private var movementFaultLatched = false
    private var lastPublishedState: TrackerState? = null
    private var lastPublishedAtMs: Long? = null
    private var started = false

    private val cameraHealthCheck = object : Runnable {
        override fun run() {
            if (!started) return
            val now = SystemClock.elapsedRealtime()
            if (!movementFaultLatched && !retryScheduled && cameraHealth.isStalled(now)) {
                scheduleCameraRetry(TimeoutException("No camera frame for ${FRAME_TIMEOUT_MS}ms"))
            }
            if (started) mainHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS)
        }
    }

    private val wakeLockRenewal = object : Runnable {
        override fun run() {
            if (!started) return
            acquireWakeLockLease()
        }
    }

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        thermalStatus = status
        frameSource?.analysisIntervalMs = ThermalController.intervalMs(status)
        ThermalController.faultReason(status)?.let(::enterFault)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        powerManager = getSystemService(PowerManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        alarm = AlarmController(AndroidToneOutput())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.addThermalStatusListener(mainExecutor, thermalListener)
            thermalStatus = powerManager.currentThermalStatus
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopTracking()
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (started) return
        started = true
        startForeground(NOTIFICATION_ID, notification("Menyiapkan kamera", TrackerState.WATCH))
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:tracker").apply {
            setReferenceCounted(false)
        }
        acquireWakeLockLease()

        val cameraId = rearCameraId()
        val loaded = CalibrationStore(this).load(cameraId)
        calibration = loaded.getOrElse {
            TrackingStateStore(this).setArmed(false)
            enterFault(it.message ?: "Kalibrasi tidak dapat dibaca")
            return
        }
        if (calibration == null) {
            TrackingStateStore(this).setArmed(false)
            enterFault("Kasur belum dikalibrasi")
            return
        }
        movementMonitor = MountMovementMonitor(calibration!!.gravity.toFloatArray())
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (!OpenCVLoader.initLocal()) {
            TrackingStateStore(this).setArmed(false)
            enterFault("OpenCV tidak dapat dimuat")
            return
        }
        try {
            objectDetector = MediaPipePhoneObjectDetector(this)
            screenDetector = ScreenCandidateDetector()
        } catch (error: Throwable) {
            TrackingStateStore(this).setArmed(false)
            enterFault("Model AI tidak dapat dimuat: ${error.javaClass.simpleName}")
            return
        }
        TrackingStateStore(this).setArmed(true)
        bindCamera()
        mainHandler.postDelayed(cameraHealthCheck, HEALTH_CHECK_INTERVAL_MS)
    }

    private fun bindCamera() {
        if (movementFaultLatched) return
        retryScheduled = false
        frameSource?.close()
        cameraHealth.onBound(SystemClock.elapsedRealtime())
        frameSource = CameraFrameSource(this).also { source ->
            source.analysisIntervalMs = ThermalController.intervalMs(thermalStatus)
            source.start(
                lifecycleOwner = this,
                onError = { error -> scheduleCameraRetry(error) },
                onFrame = ::analyzeFrame,
            )
        }
    }

    private fun scheduleCameraRetry(error: Throwable) {
        if (!started || movementFaultLatched || retryScheduled) return
        retryScheduled = true
        val delay = cameraHealth.retryDelayMs(retryAttempt)
        retryAttempt = minOf(retryAttempt + 1, MAX_RETRY_ATTEMPT)
        frameSource?.close()
        frameSource = null
        enterFault("Kamera terputus (${error.javaClass.simpleName}); retry ${delay / 1_000}s")
        mainHandler.postDelayed({
            if (started) bindCamera()
        }, delay)
    }

    private fun analyzeFrame(frame: CameraFrame) {
        val startedAt = SystemClock.elapsedRealtime()
        cameraHealth.onFrame(frame.timestampMs)
        retryAttempt = 0
        publishPreview(frame)
        try {
            val thermalFault = ThermalController.faultReason(thermalStatus)
            if (thermalFault != null) {
                enterFault(thermalFault)
                return
            }
            val currentCalibration = calibration ?: error("Calibration disappeared")
            if (volumeModel == null) {
                val characteristics = getSystemService(CameraManager::class.java)
                    .getCameraCharacteristics(currentCalibration.cameraId)
                val intrinsics = CameraPoseEstimator.intrinsicsFrom(
                    characteristics,
                    frame.bitmap.width,
                    frame.bitmap.height,
                )
                projectedVolume = CameraPoseEstimator().projectBedVolume(
                    currentCalibration,
                    intrinsics,
                    frame.bitmap.width,
                    frame.bitmap.height,
                ).getOrThrow()
                volumeModel = BedVolumeModel(projectedVolume!!)
            }
            val model = volumeModel ?: error("Bed volume unavailable")
            val evidence = mutableListOf<PhoneEvidence>()
            val mat = Mat()
            try {
                Utils.bitmapToMat(frame.bitmap, mat)
                val quality = frameQualityEvaluator.evaluate(mat)
                if (!quality.usable) {
                    enterFault(quality.faultReason ?: "Frame kamera tidak dapat dipakai")
                    return
                }
                evidence += objectDetector.orFail().detect(frame.bitmap, frame.timestampMs)
                evidence += screenDetector.orFail().detect(mat, frame.timestampMs, projectedVolume?.silhouette)
            } finally {
                mat.release()
            }
            val located = evidence.map { item -> item.copy(overlapRatio = model.overlapRatio(item.box)) }
            val tracks = trackManager.update(frame.timestampMs, located)
            val decision = decisionEngine.update(frame.timestampMs, tracks, fault = null)
            val finishedAt = SystemClock.elapsedRealtime()
            val fps = if (lastFrameAtMs == 0L || finishedAt == lastFrameAtMs) 0f else 1_000f / (finishedAt - lastFrameAtMs)
            lastFrameAtMs = finishedAt
            publish(
                state = decision.state,
                detections = located,
                inferenceMs = finishedAt - startedAt,
                analysisFps = fps,
                fault = null,
            )
        } catch (error: Throwable) {
            enterFault("Analisis gagal: ${error.javaClass.simpleName}")
        } finally {
            frame.close()
        }
    }

    private fun publish(
        state: TrackerState,
        detections: List<PhoneEvidence>,
        inferenceMs: Long,
        analysisFps: Float,
        fault: String?,
    ) {
        val now = SystemClock.elapsedRealtime()
        alarm.apply(state, now)
        val snapshot = TrackerSnapshot(
            state = state,
            detections = detections,
            projectedVolume = projectedVolume,
            inferenceMs = inferenceMs,
            analysisFps = analysisFps,
            thermalStatus = thermalStatus,
            faultReason = fault,
        )
        TrackerRuntime.statusStore.update(snapshot)
        if (cadence.shouldPublish(lastPublishedState, state, lastPublishedAtMs, now)) {
            val label = when (state) {
                TrackerState.CLEAR -> "Aman — tidak ada HP di kasur"
                TrackerState.WATCH -> "Memeriksa objek mirip HP"
                TrackerState.ALARM -> "HP terdeteksi di kasur"
                TrackerState.FAULT -> fault ?: "Pelacakan bermasalah"
            }
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(label, state))
            val strongest = detections.maxByOrNull { it.confidence * it.overlapRatio }
            Log.i(
                TAG,
                "state=$state detections=${detections.size} " +
                    "bestKind=${strongest?.kind ?: "none"} " +
                    "bestConfidence=${strongest?.confidence ?: 0f} " +
                    "bestOverlap=${strongest?.overlapRatio ?: 0f} " +
                    "inferenceMs=$inferenceMs fps=$analysisFps thermal=$thermalStatus",
            )
            lastPublishedState = state
            lastPublishedAtMs = now
        }
    }

    private fun enterFault(reason: String) {
        publish(TrackerState.FAULT, emptyList(), 0L, 0f, reason)
    }

    private fun stopTracking() {
        started = false
        TrackingStateStore(this).setArmed(false)
        releaseRuntime()
        alarm.apply(TrackerState.CLEAR, SystemClock.elapsedRealtime())
        TrackerRuntime.statusStore.update(
            TrackerSnapshot(
                state = TrackerState.FAULT,
                faultReason = "Pelacakan dihentikan",
            ),
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseRuntime() {
        mainHandler.removeCallbacksAndMessages(null)
        retryScheduled = false
        movementFaultLatched = false
        sensorManager.unregisterListener(this)
        frameSource?.close()
        frameSource = null
        objectDetector.closeSafely()
        objectDetector = null
        screenDetector = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        trackManager.clear()
        decisionEngine.reset()
        TrackerRuntime.previewStore.clear()
        lastPublishedState = null
        lastPublishedAtMs = null
    }

    private fun acquireWakeLockLease() {
        val lock = wakeLock ?: return
        if (lock.isHeld) lock.release()
        lock.acquire(cadence.wakeLockTimeoutMs)
        mainHandler.removeCallbacks(wakeLockRenewal)
        if (started) mainHandler.postAtTime(
            wakeLockRenewal,
            cadence.nextWakeLockRenewalAt(SystemClock.uptimeMillis()),
        )
    }

    private fun publishPreview(frame: CameraFrame) {
        runCatching {
            TrackerRuntime.previewStore.offer(frame.timestampMs) {
                encodePreview(frame.bitmap)
            }
        }.onFailure { error ->
            Log.w(TAG, "In-memory preview skipped: ${error.javaClass.simpleName}")
        }
    }

    private fun encodePreview(source: Bitmap): ByteArray {
        val longestSide = maxOf(source.width, source.height)
        val scale = minOf(1f, PREVIEW_MAX_DIMENSION.toFloat() / longestSide)
        val width = maxOf(1, (source.width * scale).toInt())
        val height = maxOf(1, (source.height * scale).toInt())
        val preview = if (width == source.width && height == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, width, height, true)
        }
        return try {
            ByteArrayOutputStream().use { output ->
                check(preview.compress(Bitmap.CompressFormat.JPEG, PREVIEW_JPEG_QUALITY, output))
                output.toByteArray()
            }
        } finally {
            if (preview !== source && !preview.isRecycled) preview.recycle()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val timestampMs = event.timestamp / 1_000_000L
        if (
            event.sensor.type == Sensor.TYPE_ACCELEROMETER &&
            movementMonitor?.isMoved(event.values, timestampMs) == true
        ) {
            movementFaultLatched = true
            retryScheduled = false
            frameSource?.stop()
            enterFault("Posisi kamera berubah; kalibrasi ulang diperlukan")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        started = false
        releaseRuntime()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.removeThermalStatusListener(thermalListener)
        }
        alarm.close()
        super.onDestroy()
    }

    private fun rearCameraId(): String {
        val manager = getSystemService(CameraManager::class.java)
        return manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK
        } ?: error("Kamera belakang tidak ditemukan")
    }

    private fun notification(text: String, state: TrackerState): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("IRIS — ${state.name}")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "IRIS — pelacakan kasur", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Status kamera AI lokal"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun PhoneObjectDetector?.orFail(): PhoneObjectDetector = this ?: error("Object detector unavailable")
    private fun ScreenCandidateDetector?.orFail(): ScreenCandidateDetector = this ?: error("Screen detector unavailable")
    private fun AutoCloseable?.closeSafely() = runCatching { this?.close() }.getOrNull()

    companion object {
        const val ACTION_START = "app.nophoneinbed.action.START"
        const val ACTION_STOP = "app.nophoneinbed.action.STOP"
        private const val CHANNEL_ID = "bed_tracking"
        private const val NOTIFICATION_ID = 41
        private const val TAG = "IRIS"
        private const val FRAME_TIMEOUT_MS = 10_000L
        private const val HEALTH_CHECK_INTERVAL_MS = 2_000L
        private const val PUBLISH_INTERVAL_MS = 1_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 12L * 60L * 60L * 1_000L
        private const val WAKE_LOCK_RENEWAL_MS = 6L * 60L * 60L * 1_000L
        private const val MAX_RETRY_ATTEMPT = 6
        private const val PREVIEW_MAX_DIMENSION = 640
        private const val PREVIEW_JPEG_QUALITY = 72

        fun startIntent(context: Context) = Intent(context, TrackerForegroundService::class.java).setAction(ACTION_START)
        fun stopIntent(context: Context) = Intent(context, TrackerForegroundService::class.java).setAction(ACTION_STOP)
    }
}
