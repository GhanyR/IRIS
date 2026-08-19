package app.nophoneinbed.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nophoneinbed.domain.EvidenceKind
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
class ScreenCandidateDetectorTest {
    @Test
    fun brightPhoneShapedRectangleProducesWatchEvidence() {
        val frame = Mat.zeros(480, 640, CvType.CV_8UC4)
        Imgproc.rectangle(frame, Point(250.0, 130.0), Point(350.0, 330.0), Scalar(255.0, 255.0, 255.0, 255.0), -1)

        val results = ScreenCandidateDetector().detect(frame, timestampMs = 100)

        assertThat(results).hasSize(1)
        assertThat(results.single().kind).isEqualTo(EvidenceKind.LUMINOUS_SCREEN)
        assertThat(results.single().confidence).isLessThan(0.35f)
        frame.release()
    }

    @Test
    fun squareAndVeryThinShapesAreRejected() {
        val frame = Mat.zeros(480, 640, CvType.CV_8UC4)
        Imgproc.rectangle(frame, Point(50.0, 50.0), Point(150.0, 150.0), Scalar(255.0, 255.0, 255.0, 255.0), -1)
        Imgproc.rectangle(frame, Point(250.0, 50.0), Point(270.0, 350.0), Scalar(255.0, 255.0, 255.0, 255.0), -1)

        assertThat(ScreenCandidateDetector().detect(frame, timestampMs = 100)).isEmpty()
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
