package app.nophoneinbed.vision

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc

data class FrameQuality(
    val usable: Boolean,
    val meanLuma: Double,
    val lumaStdDev: Double,
    val faultReason: String?,
)

class FrameQualityEvaluator {
    fun evaluate(frame: Mat): FrameQuality {
        require(!frame.empty()) { "Frame must not be empty" }
        val gray = Mat()
        val mean = MatOfDouble()
        val standardDeviation = MatOfDouble()
        try {
            when (frame.channels()) {
                4 -> Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGBA2GRAY)
                3 -> Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGB2GRAY)
                1 -> frame.copyTo(gray)
                else -> error("Unsupported frame channel count: ${frame.channels()}")
            }
            Core.meanStdDev(gray, mean, standardDeviation)
            val meanLuma = mean.toArray().first()
            val lumaStdDev = standardDeviation.toArray().first()
            val reason = when {
                meanLuma < MIN_MEAN_LUMA -> "Frame terlalu gelap; nyalakan lampu atau buka lensa"
                lumaStdDev < MIN_LUMA_STD_DEV -> "Kamera tertutup atau scene tidak memiliki detail"
                else -> null
            }
            return FrameQuality(
                usable = reason == null,
                meanLuma = meanLuma,
                lumaStdDev = lumaStdDev,
                faultReason = reason,
            )
        } finally {
            gray.release()
            mean.release()
            standardDeviation.release()
        }
    }

    companion object {
        private const val MIN_MEAN_LUMA = 25.0
        private const val MIN_LUMA_STD_DEV = 6.0
    }
}
