package app.nophoneinbed

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.nophoneinbed.data.CalibrationStore
import app.nophoneinbed.data.TrackingStateStore
import app.nophoneinbed.databinding.ActivityMainBinding
import app.nophoneinbed.domain.BedCalibration
import app.nophoneinbed.domain.BedVolumeProjection
import app.nophoneinbed.domain.TrackerState
import app.nophoneinbed.manager.ManagerAction
import app.nophoneinbed.runtime.AndroidToneOutput
import app.nophoneinbed.runtime.TrackerForegroundService
import app.nophoneinbed.runtime.TrackerRuntime
import app.nophoneinbed.runtime.TrackerSnapshot
import app.nophoneinbed.ui.CalibrationController
import app.nophoneinbed.ui.OverlayRenderState
import app.nophoneinbed.vision.CameraFrameSource
import app.nophoneinbed.vision.CameraPoseEstimator
import app.nophoneinbed.vision.CoordinateMapper
import app.nophoneinbed.vision.PreviewScaleMode
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var calibrationStore: CalibrationStore
    private lateinit var trackingStateStore: TrackingStateStore
    private lateinit var sensorManager: SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var calibrationController = CalibrationController()
    private var setupCamera: CameraFrameSource? = null
    private var currentCalibration: BedCalibration? = null
    private var currentProjection: BedVolumeProjection? = null
    private var currentGravity = floatArrayOf(0f, 9.80665f, 0f)
    private var latestImageWidth = 0
    private var latestImageHeight = 0
    private var calibrating = false
    private var audibleAcknowledged = false
    private var trackingRequested = false
    private var testTone: AndroidToneOutput? = null
    private var monitoringBitmap: Bitmap? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.CAMERA] == true || hasCameraPermission()) {
            if (trackingRequested) {
                ContextCompat.startForegroundService(this, TrackerForegroundService.startIntent(this))
            } else {
                startSetupPreview()
            }
        } else {
            showSetupMessage("Izin kamera diperlukan agar AI bisa melihat kasur", fault = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        calibrationStore = CalibrationStore(this)
        trackingStateStore = TrackingStateStore(this)
        trackingRequested = trackingStateStore.isArmed()
        sensorManager = getSystemService(SensorManager::class.java)
        configureActions()
        restoreCalibration()
        updateAlarmVolume()
        observeTrackerStatus()
        if (hasRequiredPermissions()) {
            if (handleManagerAction(intent?.action)) {
                intent?.action = Intent.ACTION_MAIN
            } else if (trackingRequested) {
                showSetupMessage("WATCH — menyambungkan kembali live monitoring", false)
                ContextCompat.startForegroundService(this, TrackerForegroundService.startIntent(this))
            } else {
                startSetupPreview()
            }
        } else {
            requestRequiredPermissions()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (hasRequiredPermissions()) {
            handleManagerAction(intent.action)
            intent.action = Intent.ACTION_MAIN
        } else {
            requestRequiredPermissions()
        }
    }

    private fun handleManagerAction(action: String?): Boolean {
        return when (ManagerAction.fromIntentAction(action)) {
            ManagerAction.START -> {
                if (currentCalibration == null) {
                    showSetupMessage("SETUP — kalibrasikan 4 sudut kasur sebelum mulai", fault = true)
                    startSetupPreview()
                } else {
                    audibleAcknowledged = true
                    startTracking()
                }
                true
            }
            ManagerAction.STOP -> {
                stopTracking()
                true
            }
            ManagerAction.TEST_ALARM -> {
                testAlarm()
                true
            }
            null -> false
        }
    }

    private fun configureActions() {
        binding.overlayView.onNormalizedTap = { point ->
            if (calibrating && calibrationController.onTap(point)) {
                renderOverlay()
                updateCalibrationPrompt()
                updateControls()
            }
        }
        binding.overlayView.onNormalizedDrag = { index, point ->
            if (calibrating && calibrationController.moveCorner(index, point)) {
                currentProjection = null
                renderOverlay()
                updateCalibrationPrompt()
                updateControls()
            }
        }
        binding.calibrateButton.setOnClickListener {
            calibrationController = CalibrationController()
            currentProjection = null
            calibrating = true
            audibleAcknowledged = false
            if (hasCameraPermission()) startSetupPreview() else requestRequiredPermissions()
            renderOverlay()
            updateCalibrationPrompt()
            updateControls()
        }
        binding.undoButton.setOnClickListener {
            calibrationController.undo()
            currentProjection = null
            renderOverlay()
            updateCalibrationPrompt()
            updateControls()
        }
        binding.resetButton.setOnClickListener {
            calibrationStore.clear()
            calibrationController.reset()
            currentCalibration = null
            currentProjection = null
            audibleAcknowledged = false
            calibrating = true
            renderOverlay()
            updateCalibrationPrompt()
            updateControls()
        }
        binding.saveButton.setOnClickListener { saveCalibration() }
        binding.testAlarmButton.setOnClickListener { testAlarm() }
        binding.startButton.setOnClickListener { startTracking() }
        binding.stopButton.setOnClickListener { stopTracking() }
        binding.batteryButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (!hasCameraPermission()) permissions += Manifest.permission.CAMERA
        if (!hasNotificationPermission()) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startSetupPreview() {
        if (trackingRequested || setupCamera?.isRunning == true || !hasCameraPermission()) return
        setupCamera?.close()
        setupCamera = CameraFrameSource(this).also { source ->
            source.analysisIntervalMs = 500L
            source.start(
                lifecycleOwner = this,
                surfaceProvider = binding.previewView.surfaceProvider,
                onError = { error ->
                    runOnUiThread { showSetupMessage("Kamera gagal dibuka: ${error.javaClass.simpleName}", true) }
                },
                onFrame = { frame ->
                    try {
                        latestImageWidth = frame.bitmap.width
                        latestImageHeight = frame.bitmap.height
                        runOnUiThread {
                            if (binding.overlayView.width > 0 && binding.overlayView.height > 0) {
                                binding.overlayView.coordinateMapper = CoordinateMapper(
                                    analysisWidth = latestImageWidth,
                                    analysisHeight = latestImageHeight,
                                    previewWidth = binding.overlayView.width,
                                    previewHeight = binding.overlayView.height,
                                    rotationDegrees = 0,
                                    scaleMode = PreviewScaleMode.FIT_CENTER,
                                )
                                if (currentCalibration != null && currentProjection == null) {
                                    validateProjection(currentCalibration!!).onSuccess { projection ->
                                        currentProjection = projection
                                        renderOverlay()
                                    }
                                }
                                updateControls()
                            }
                        }
                    } finally {
                        frame.close()
                    }
                },
            )
        }
    }

    private fun saveCalibration() {
        val result = runCatching {
            val calibration = calibrationController.build(
                widthMeters = binding.widthInput.text.toString().toFloat(),
                lengthMeters = binding.lengthInput.text.toString().toFloat(),
                heightMeters = binding.heightInput.text.toString().toFloat(),
                gravity = currentGravity.toList(),
                cameraId = rearCameraId(),
            ).getOrThrow()
            validateProjection(calibration).getOrThrow() to calibration
        }
        result.onSuccess { (projection, calibration) ->
            calibrationStore.save(calibration)
            currentCalibration = calibration
            currentProjection = projection
            calibrating = false
            audibleAcknowledged = false
            showSetupMessage("READY — kalibrasi valid, sekarang tes bunyi", fault = false)
            binding.diagnosticText.text = "Pose RMS %.2f px • area 3D aktif".format(projection.reprojectionErrorPx)
            renderOverlay()
            updateControls()
        }.onFailure { error ->
            showSetupMessage("Kalibrasi belum valid: ${error.message}", fault = true)
        }
    }

    private fun validateProjection(calibration: BedCalibration): Result<BedVolumeProjection> = runCatching {
        require(latestImageWidth > 0 && latestImageHeight > 0) { "Tunggu frame kamera muncul" }
        require(OpenCVLoader.initLocal()) { "OpenCV tidak dapat dimuat" }
        val characteristics = getSystemService(CameraManager::class.java)
            .getCameraCharacteristics(calibration.cameraId)
        val intrinsics = CameraPoseEstimator.intrinsicsFrom(characteristics, latestImageWidth, latestImageHeight)
        CameraPoseEstimator().projectBedVolume(
            calibration,
            intrinsics,
            latestImageWidth,
            latestImageHeight,
        ).getOrThrow()
    }

    private fun testAlarm() {
        testTone?.close()
        testTone = AndroidToneOutput().also { it.startLoop() }
        audibleAcknowledged = true
        showSetupMessage("Tes bunyi aktif 2 detik — pastikan terdengar dari posisi kasur", false)
        mainHandler.postDelayed({
            testTone?.close()
            testTone = null
        }, 2_000L)
        updateControls()
    }

    private fun startTracking() {
        if (currentCalibration == null || !audibleAcknowledged) return
        setupCamera?.close()
        setupCamera = null
        trackingRequested = true
        trackingStateStore.setArmed(true)
        binding.previewView.visibility = View.GONE
        ContextCompat.startForegroundService(this, TrackerForegroundService.startIntent(this))
        showSetupMessage("WATCH — AI sedang memulai kamera", false)
        updateControls()
    }

    private fun stopTracking() {
        startService(TrackerForegroundService.stopIntent(this))
        trackingRequested = false
        trackingStateStore.setArmed(false)
        clearMonitoringPreview()
        binding.previewView.visibility = View.VISIBLE
        showSetupMessage("STOPPED — pelacakan tidak aktif", true)
        mainHandler.postDelayed(::startSetupPreview, 600L)
        updateControls()
    }

    private fun observeTrackerStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { TrackerRuntime.statusStore.status.collect(::renderTrackerSnapshot) }
                launch { TrackerRuntime.previewStore.frames.collect(::renderMonitoringPreview) }
            }
        }
    }

    private fun renderTrackerSnapshot(snapshot: TrackerSnapshot) {
        if (trackingStateStore.isArmed()) trackingRequested = true
        if (trackingRequested || snapshot.inferenceMs > 0L) {
            val label = when (snapshot.state) {
                TrackerState.CLEAR -> "CLEAR — tidak ada HP terdeteksi"
                TrackerState.WATCH -> "WATCH — memeriksa objek"
                TrackerState.ALARM -> "ALARM — HP ada di area kasur"
                TrackerState.FAULT -> "FAULT — ${snapshot.faultReason ?: "periksa sistem"}"
            }
            binding.stateText.text = label
            binding.stateText.setTextColor(colorFor(snapshot.state))
            binding.diagnosticText.text = "AI ${snapshot.inferenceMs} ms • %.1f FPS • thermal %d • objek %d".format(
                snapshot.analysisFps,
                snapshot.thermalStatus,
                snapshot.detections.size,
            )
            binding.overlayView.render(
                OverlayRenderState(
                    calibrationCorners = currentCalibration?.mattressCorners.orEmpty(),
                    projectedVolume = snapshot.projectedVolume?.silhouette ?: currentProjection?.silhouette,
                    detections = snapshot.detections,
                    trackerState = snapshot.state,
                ),
            )
        }
    }

    private fun renderMonitoringPreview(preview: app.nophoneinbed.runtime.InMemoryPreview?) {
        if (preview == null || !trackingRequested) {
            clearMonitoringPreview()
            return
        }
        val bytes = preview.jpegBytes()
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val previous = monitoringBitmap
        monitoringBitmap = decoded
        binding.monitoringPreviewView.setImageBitmap(decoded)
        binding.monitoringPreviewView.visibility = View.VISIBLE
        binding.previewView.visibility = View.GONE
        if (binding.overlayView.width > 0 && binding.overlayView.height > 0) {
            binding.overlayView.coordinateMapper = CoordinateMapper(
                analysisWidth = decoded.width,
                analysisHeight = decoded.height,
                previewWidth = binding.overlayView.width,
                previewHeight = binding.overlayView.height,
                rotationDegrees = 0,
                scaleMode = PreviewScaleMode.FIT_CENTER,
            )
        }
        previous?.takeIf { !it.isRecycled }?.recycle()
    }

    private fun clearMonitoringPreview() {
        binding.monitoringPreviewView.setImageDrawable(null)
        binding.monitoringPreviewView.visibility = View.GONE
        monitoringBitmap?.takeIf { !it.isRecycled }?.recycle()
        monitoringBitmap = null
    }

    private fun restoreCalibration() {
        val cameraId = runCatching(::rearCameraId).getOrDefault(CalibrationStore.DEFAULT_CAMERA_ID)
        calibrationStore.load(cameraId).onSuccess { calibration ->
            currentCalibration = calibration
            calibrationController = CalibrationController().also { controller ->
                calibration?.mattressCorners?.forEach(controller::onTap)
            }
            if (calibration != null) {
                binding.widthInput.setText(calibration.widthMeters.toString())
                binding.lengthInput.setText(calibration.lengthMeters.toString())
                binding.heightInput.setText(calibration.heightMeters.toString())
                showSetupMessage("READY — kalibrasi tersimpan; tes bunyi sebelum Mulai", false)
            } else {
                calibrating = true
            }
        }.onFailure {
            calibrationStore.clear()
            currentCalibration = null
            calibrating = true
            showSetupMessage("Kamera berubah; lakukan kalibrasi ulang", true)
        }
        updateCalibrationPrompt()
        renderOverlay()
        updateControls()
    }

    private fun updateCalibrationPrompt() {
        binding.promptText.text = calibrationController.nextPrompt?.let {
            "Tap titik ${calibrationController.corners.size + 1}: ${it.label}. Seluruh kasur harus tetap terlihat."
        } ?: "Empat titik lengkap. Drag titik dengan mouse dari Mac sampai presisi, lalu Simpan 4 titik."
    }

    private fun renderOverlay() {
        binding.overlayView.calibrationEditable = calibrating && !trackingRequested
        binding.overlayView.render(
            OverlayRenderState(
                calibrationCorners = calibrationController.corners,
                projectedVolume = currentProjection?.silhouette,
                trackerState = if (currentCalibration == null) TrackerState.WATCH else TrackerState.CLEAR,
            ),
        )
    }

    private fun updateControls() {
        binding.calibrateButton.isEnabled = !trackingRequested
        binding.undoButton.isEnabled = calibrationController.corners.isNotEmpty() && calibrating && !trackingRequested
        binding.resetButton.isEnabled = !trackingRequested
        binding.saveButton.isEnabled = calibrationController.corners.size == 4 && latestImageWidth > 0 && !trackingRequested
        binding.testAlarmButton.isEnabled = currentCalibration != null
        binding.startButton.isEnabled = currentCalibration != null && audibleAcknowledged && !trackingRequested
        binding.stopButton.isEnabled = trackingRequested
    }

    private fun updateAlarmVolume() {
        val audio = getSystemService(AudioManager::class.java)
        val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        binding.alarmVolumeText.text = if (current == 0) {
            "Volume alarm $current/$maximum — naikkan volume sebelum mulai"
        } else {
            "Volume alarm $current/$maximum"
        }
        binding.alarmVolumeText.setTextColor(if (current == 0) getColor(R.color.tracker_alarm) else getColor(R.color.tracker_muted))
    }

    private fun showSetupMessage(message: String, fault: Boolean) {
        binding.stateText.text = message
        binding.stateText.setTextColor(getColor(if (fault) R.color.tracker_fault else R.color.tracker_watch))
    }

    private fun colorFor(state: TrackerState): Int = getColor(
        when (state) {
            TrackerState.CLEAR -> R.color.tracker_clear
            TrackerState.WATCH -> R.color.tracker_watch
            TrackerState.ALARM -> R.color.tracker_alarm
            TrackerState.FAULT -> R.color.tracker_fault
        },
    )

    private fun rearCameraId(): String {
        val manager = getSystemService(CameraManager::class.java)
        return manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK
        } ?: error("Kamera belakang tidak ditemukan")
    }

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun hasRequiredPermissions(): Boolean = hasCameraPermission() && hasNotificationPermission()

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        updateAlarmVolume()
    }

    override fun onStart() {
        super.onStart()
        TrackerRuntime.previewStore.setConsumerActive(true)
    }

    override fun onStop() {
        TrackerRuntime.previewStore.setConsumerActive(false)
        clearMonitoringPreview()
        super.onStop()
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) currentGravity = event.values.copyOf()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        setupCamera?.close()
        testTone?.close()
        clearMonitoringPreview()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
