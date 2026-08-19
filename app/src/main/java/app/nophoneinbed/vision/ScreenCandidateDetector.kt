package app.nophoneinbed.vision

import app.nophoneinbed.domain.EvidenceKind
import app.nophoneinbed.domain.NRect
import app.nophoneinbed.domain.PhoneEvidence
import app.nophoneinbed.domain.Polygon
import kotlin.math.max
import kotlin.math.min
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class ScreenCandidateDetector {
    fun detect(
        frame: Mat,
        timestampMs: Long,
        region: Polygon? = null,
    ): List<PhoneEvidence> {
        require(!frame.empty()) { "Frame must not be empty" }
        require(timestampMs >= 0) { "Timestamp must be non-negative" }

        val gray = Mat()
        val mask = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val contours = mutableListOf<MatOfPoint>()
        try {
            when (frame.channels()) {
                4 -> Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGBA2GRAY)
                3 -> Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGB2GRAY)
                1 -> frame.copyTo(gray)
                else -> error("Unsupported frame channel count: ${frame.channels()}")
            }
            val threshold = max(160.0, percentile(gray, 0.95))
            Imgproc.threshold(gray, mask, threshold, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val frameArea = frame.rows().toDouble() * frame.cols()
            return contours.mapNotNull { contour ->
                val area = Imgproc.contourArea(contour)
                val areaRatio = area / frameArea
                if (areaRatio !in MIN_AREA_RATIO..MAX_AREA_RATIO) return@mapNotNull null

                val rect = Imgproc.boundingRect(contour)
                val shortSide = min(rect.width, rect.height).toDouble()
                val longSide = max(rect.width, rect.height).toDouble()
                if (shortSide <= 0.0) return@mapNotNull null
                val aspectRatio = longSide / shortSide
                if (aspectRatio !in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO) return@mapNotNull null

                val fillRatio = area / (rect.width.toDouble() * rect.height)
                if (fillRatio < MIN_FILL_RATIO) return@mapNotNull null

                val curve = MatOfPoint2f(*contour.toArray())
                val approximation = MatOfPoint2f()
                try {
                    val perimeter = Imgproc.arcLength(curve, true)
                    Imgproc.approxPolyDP(curve, approximation, 0.02 * perimeter, true)
                    if (approximation.total() != 4L) return@mapNotNull null
                } finally {
                    curve.release()
                    approximation.release()
                }

                val normalized = NRect(
                    left = rect.x.toFloat() / frame.cols(),
                    top = rect.y.toFloat() / frame.rows(),
                    right = (rect.x + rect.width).toFloat() / frame.cols(),
                    bottom = (rect.y + rect.height).toFloat() / frame.rows(),
                )
                if (region != null && !region.contains(normalized.center)) return@mapNotNull null

                PhoneEvidence(
                    box = normalized,
                    confidence = min(0.34f, (0.20 + fillRatio * 0.12).toFloat()),
                    kind = EvidenceKind.LUMINOUS_SCREEN,
                    overlapRatio = 0f,
                    timestampMs = timestampMs,
                )
            }
        } finally {
            contours.forEach(MatOfPoint::release)
            gray.release()
            mask.release()
            hierarchy.release()
            kernel.release()
        }
    }

    private fun percentile(gray: Mat, fraction: Double): Double {
        val histogram = Mat()
        return try {
            Imgproc.calcHist(
                listOf(gray),
                MatOfInt(0),
                Mat(),
                histogram,
                MatOfInt(256),
                MatOfFloat(0f, 256f),
            )
            val target = gray.total() * fraction
            var accumulated = 0.0
            for (index in 0 until 256) {
                accumulated += histogram.get(index, 0)[0]
                if (accumulated >= target) return index.toDouble()
            }
            255.0
        } finally {
            histogram.release()
        }
    }

    companion object {
        private const val MIN_AREA_RATIO = 0.0003
        private const val MAX_AREA_RATIO = 0.15
        private const val MIN_ASPECT_RATIO = 1.25
        private const val MAX_ASPECT_RATIO = 2.6
        private const val MIN_FILL_RATIO = 0.70
    }
}
