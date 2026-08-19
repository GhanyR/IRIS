package app.nophoneinbed.vision

import android.content.Context
import android.graphics.Bitmap
import app.nophoneinbed.domain.EvidenceKind
import app.nophoneinbed.domain.NRect
import app.nophoneinbed.domain.PhoneEvidence
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

class MediaPipePhoneObjectDetector(context: Context) : PhoneObjectDetector {
    override val categoryAllowlist: Set<String> = setOf(CELL_PHONE_CATEGORY)

    private val detector: ObjectDetector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .build(),
            )
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(5)
            .setScoreThreshold(0.20f)
            .setCategoryAllowlist(categoryAllowlist.toList())
            .build(),
    )

    override fun detect(bitmap: Bitmap, timestampMs: Long): List<PhoneEvidence> {
        require(timestampMs >= 0) { "Timestamp must be non-negative" }
        // MPImage owns and releases the Bitmap passed to BitmapImageBuilder. Keep the
        // camera frame alive because the same frame is also consumed by OpenCV.
        val detectorBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: error("Unable to copy camera frame for object detection")
        val image = BitmapImageBuilder(detectorBitmap).build()
        return try {
            detector.detect(image).detections().mapNotNull { detection ->
                val category = detection.categories()
                    .filter { it.categoryName() == CELL_PHONE_CATEGORY }
                    .maxByOrNull { it.score() }
                    ?: return@mapNotNull null
                val box = detection.boundingBox()
                val left = (box.left / detectorBitmap.width).coerceIn(0f, 1f)
                val top = (box.top / detectorBitmap.height).coerceIn(0f, 1f)
                val right = (box.right / detectorBitmap.width).coerceIn(0f, 1f)
                val bottom = (box.bottom / detectorBitmap.height).coerceIn(0f, 1f)
                if (right <= left || bottom <= top) return@mapNotNull null
                PhoneEvidence(
                    box = NRect(left, top, right, bottom),
                    confidence = category.score().coerceIn(0f, 1f),
                    kind = EvidenceKind.OBJECT_MODEL,
                    overlapRatio = 0f,
                    timestampMs = timestampMs,
                )
            }
        } finally {
            image.close()
            if (!detectorBitmap.isRecycled) detectorBitmap.recycle()
        }
    }

    override fun close() {
        detector.close()
    }

    companion object {
        private const val MODEL_ASSET = "efficientdet_lite0.tflite"
        private const val CELL_PHONE_CATEGORY = "cell phone"
    }
}
