package app.nophoneinbed.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class CameraFrame(
    val bitmap: Bitmap,
    val timestampMs: Long,
    val rotationDegrees: Int,
    val cameraId: String = "0",
) : Closeable {
    override fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

class CameraFrameSource(private val context: Context) : Closeable {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private var provider: ProcessCameraProvider? = null
    @Volatile var analysisIntervalMs: Long = 200L
    @Volatile private var lastDeliveredAtMs = 0L

    val isRunning: Boolean get() = running.get()

    fun start(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider? = null,
        onError: (Throwable) -> Unit = {},
        onFrame: (CameraFrame) -> Unit,
    ) {
        if (!running.compareAndSet(false, true)) return
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (!running.get()) return@addListener
            try {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also { useCase ->
                    surfaceProvider?.let(useCase::setSurfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor) { image -> analyze(image, onFrame) }
                cameraProvider.unbindAll()
                val useCases = if (surfaceProvider == null) arrayOf(analysis) else arrayOf(preview, analysis)
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    *useCases,
                )
            } catch (error: Throwable) {
                running.set(false)
                onError(error)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyze(image: ImageProxy, onFrame: (CameraFrame) -> Unit) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (!running.get() || now - lastDeliveredAtMs < analysisIntervalMs) return
            lastDeliveredAtMs = now
            val source = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val bitmap = if (rotation == 0) {
                source
            } else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
                    if (it !== source) source.recycle()
                }
            }
            onFrame(
                CameraFrame(
                    bitmap = bitmap,
                    timestampMs = now,
                    rotationDegrees = 0,
                ),
            )
        } finally {
            image.close()
        }
    }

    fun stop() {
        running.set(false)
        ContextCompat.getMainExecutor(context).execute {
            provider?.unbindAll()
            provider = null
        }
    }

    override fun close() {
        stop()
        executor.shutdownNow()
    }
}
