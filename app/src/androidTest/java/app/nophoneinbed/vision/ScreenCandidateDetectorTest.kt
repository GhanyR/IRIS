package app.nophoneinbed.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nophoneinbed.domain.EvidenceKind
import app.nophoneinbed.domain.Polygon
import com.google.common.truth.Truth.assertThat
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.MatOfPoint
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

    @Test
    fun isolatedDarkPhoneShapeProducesConfirmationEvidence() {
        val frame = Mat(480, 640, CvType.CV_8UC4, Scalar(175.0, 175.0, 175.0, 255.0))
        Imgproc.rectangle(frame, Point(286.0, 190.0), Point(326.0, 252.0), Scalar(20.0, 20.0, 20.0, 255.0), -1)

        val results = ScreenCandidateDetector().detect(frame, timestampMs = 200)

        val darkShape = results.single { it.kind == EvidenceKind.DARK_PHONE_SHAPE }
        assertThat(darkShape.confidence).isAtLeast(0.35f)
        frame.release()
    }

    @Test
    fun lowContrastDarkRectangleIsRejected() {
        val frame = Mat(480, 640, CvType.CV_8UC4, Scalar(70.0, 70.0, 70.0, 255.0))
        Imgproc.rectangle(frame, Point(286.0, 190.0), Point(326.0, 252.0), Scalar(45.0, 45.0, 45.0, 255.0), -1)

        val results = ScreenCandidateDetector().detect(frame, timestampMs = 300)

        assertThat(results.none { it.kind == EvidenceKind.DARK_PHONE_SHAPE }).isTrue()
        frame.release()
    }

    @Test
    fun darkPhonesSurvivePortraitLandscapeAndObliquePerspective() {
        val frame = Mat(480, 640, CvType.CV_8UC4, Scalar(175.0, 175.0, 175.0, 255.0))
        Imgproc.rectangle(frame, Point(70.0, 90.0), Point(110.0, 160.0), Scalar(18.0, 18.0, 18.0, 255.0), -1)
        Imgproc.rectangle(frame, Point(230.0, 100.0), Point(305.0, 142.0), Scalar(25.0, 25.0, 25.0, 255.0), -1)
        val oblique = MatOfPoint(
            Point(430.0, 95.0),
            Point(475.0, 107.0),
            Point(490.0, 176.0),
            Point(445.0, 164.0),
        )
        Imgproc.fillConvexPoly(frame, oblique, Scalar(22.0, 22.0, 22.0, 255.0))

        val darkShapes = ScreenCandidateDetector().detect(frame, timestampMs = 400)
            .filter { it.kind == EvidenceKind.DARK_PHONE_SHAPE }

        assertThat(darkShapes).hasSize(3)
        assertThat(darkShapes.all { it.confidence >= 0.35f }).isTrue()
        oblique.release()
        frame.release()
    }

    @Test
    fun darkClutterShapesAreRejectedByScaleAspectAndFill() {
        val frame = Mat(480, 640, CvType.CV_8UC4, Scalar(175.0, 175.0, 175.0, 255.0))
        Imgproc.rectangle(frame, Point(35.0, 50.0), Point(95.0, 110.0), Scalar(20.0, 20.0, 20.0, 255.0), -1)
        Imgproc.rectangle(frame, Point(160.0, 40.0), Point(172.0, 240.0), Scalar(20.0, 20.0, 20.0, 255.0), -1)
        Imgproc.rectangle(frame, Point(250.0, 50.0), Point(470.0, 210.0), Scalar(20.0, 20.0, 20.0, 255.0), -1)
        Imgproc.rectangle(frame, Point(500.0, 60.0), Point(555.0, 160.0), Scalar(20.0, 20.0, 20.0, 255.0), -1)
        Imgproc.rectangle(frame, Point(555.0, 125.0), Point(610.0, 160.0), Scalar(20.0, 20.0, 20.0, 255.0), -1)

        val darkShapes = ScreenCandidateDetector().detect(frame, timestampMs = 500)
            .filter { it.kind == EvidenceKind.DARK_PHONE_SHAPE }

        assertThat(darkShapes).isEmpty()
        frame.release()
    }

    @Test
    fun phoneShapeOutsideCalibratedBedRegionIsRejected() {
        val frame = Mat(480, 640, CvType.CV_8UC4, Scalar(175.0, 175.0, 175.0, 255.0))
        Imgproc.rectangle(frame, Point(500.0, 180.0), Point(545.0, 250.0), Scalar(20.0, 20.0, 20.0, 255.0), -1)
        val bedRegion = Polygon.rectangle(.05f, .1f, .65f, .9f)

        val results = ScreenCandidateDetector().detect(frame, timestampMs = 600, region = bedRegion)

        assertThat(results.none { it.kind == EvidenceKind.DARK_PHONE_SHAPE }).isTrue()
        frame.release()
    }

    @Test
    fun brightPhoneSizedCardRemainsWatchOnlyEvidence() {
        val frame = Mat.zeros(480, 640, CvType.CV_8UC4)
        Imgproc.rectangle(frame, Point(280.0, 170.0), Point(330.0, 250.0), Scalar(255.0, 255.0, 255.0, 255.0), -1)

        val result = ScreenCandidateDetector().detect(frame, timestampMs = 700).single()

        assertThat(result.kind).isEqualTo(EvidenceKind.LUMINOUS_SCREEN)
        assertThat(result.confidence).isLessThan(0.35f)
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
