package app.nophoneinbed.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import app.nophoneinbed.vision.MediaPipePhoneObjectDetector
import app.nophoneinbed.vision.PhoneObjectDetector
import app.nophoneinbed.vision.ScreenCandidateDetector
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat

class TrackerForegroundService : LifecycleService(), SensorEventListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val trackManager = PhoneTrackManager(occlusionRetentionMs = 10_000L)
    private val decisionEngine = DetectionDecisionEngine(DecisionPolicy.default())
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
    private var started = false

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
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }

        val cameraId = rearCameraId()
        val loaded = CalibrationStore(this).load(cameraId)
        calibration = loaded.getOrElse {
            enterFault(it.message ?: "Kalibrasi tidak dapat dibaca")
            return
        }
        if (calibration == null) {
            enterFault("Kasur belum dikalibrasi")
            return
        }
        movementMonitor = MountMovementMonitor(calibration!!.gravity.toFloatArray())
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (!OpenCVLoader.initLocal()) {
            enterFault("OpenCV tidak dapat dimuat")
            return
        }
        try {
            objectDetector = MediaPipePhoneObjectDetector(this)
            screenDetector = ScreenCandidateDetector()
        } catch (error: Throwable) {
            enterFault("Model AI tidak dapat dimuat: ${error.javaClass.simpleName}")
            return
        }
        bindCamera()
    }

    private fun bindCamera() {
        frameSource?.close()
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
        if (!started) return
        val delays = longArrayOf(1_000L, 2_000L, 4_000L)
        if (retryAttempt >= delays.size) {
            enterFault("Kamera gagal dibuka: ${error.javaClass.simpleName}")
            return
        }
        val delay = delays[retryAttempt++]
        enterFault("Kamera terputus; mencoba lagi")
        mainHandler.postDelayed({ if (started) bindCamera() }, delay)
    }

    private fun analyzeFrame(frame: CameraFrame) {
        val startedAt = SystemClock.elapsedRealtime()
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
            evidence += objectDetector.orFail().detect(frame.bitmap, frame.timestampMs)
            val mat = Mat()
            try {
                Utils.bitmapToMat(frame.bitmap, mat)
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
            retryAttempt = 0
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
    }

    private fun enterFault(reason: String) {
        publish(TrackerState.FAULT, emptyList(), 0L, 0f, reason)
    }

    private fun stopTracking() {
        started = false
        releaseRuntime()
        enterFault("Pelacakan dihentikan")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseRuntime() {
        mainHandler.removeCallbacksAndMessages(null)
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
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && movementMonitor?.isMoved(event.values) == true) {
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
        private const val WAKE_LOCK_TIMEOUT_MS = 12L * 60L * 60L * 1_000L

        fun startIntent(context: Context) = Intent(context, TrackerForegroundService::class.java).setAction(ACTION_START)
        fun stopIntent(context: Context) = Intent(context, TrackerForegroundService::class.java).setAction(ACTION_STOP)
    }
}
