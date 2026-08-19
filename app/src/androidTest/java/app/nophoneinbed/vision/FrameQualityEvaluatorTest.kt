package app.nophoneinbed.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

@RunWith(AndroidJUnit4::class)
class FrameQualityEvaluatorTest {
    @Test
    fun blackCoveredLensIsAVisibleFault() {
        val frame = Mat.zeros(480, 640, CvType.CV_8UC4)

        val quality = FrameQualityEvaluator().evaluate(frame)

        assertThat(quality.usable).isFalse()
        assertThat(quality.faultReason).contains("gelap")
        frame.release()
    }

    @Test
    fun flatFeaturelessFrameIsAVisibleFault() {
        val frame = Mat(480, 640, CvType.CV_8UC4, Scalar(110.0, 110.0, 110.0, 255.0))

        val quality = FrameQualityEvaluator().evaluate(frame)

        assertThat(quality.usable).isFalse()
        assertThat(quality.faultReason).contains("tertutup")
        frame.release()
    }

    @Test
    fun normallyExposedTexturedSceneIsUsable() {
        val frame = Mat(480, 640, CvType.CV_8UC4, Scalar(150.0, 150.0, 150.0, 255.0))
        Imgproc.rectangle(frame, Point(50.0, 50.0), Point(300.0, 250.0), Scalar(80.0, 95.0, 110.0, 255.0), -1)
        Imgproc.rectangle(frame, Point(350.0, 150.0), Point(580.0, 420.0), Scalar(205.0, 190.0, 175.0, 255.0), -1)

        val quality = FrameQualityEvaluator().evaluate(frame)

        assertThat(quality.usable).isTrue()
        assertThat(quality.faultReason).isNull()
        frame.release()
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun loadOpenCv() {
            assertThat(OpenCVLoader.initLocal()).isTrue()
        }
    }
}
