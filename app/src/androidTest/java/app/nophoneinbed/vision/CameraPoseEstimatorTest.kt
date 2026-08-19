package app.nophoneinbed.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nophoneinbed.domain.BedCalibration
import app.nophoneinbed.domain.NPoint
import com.google.common.truth.Truth.assertThat
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3

@RunWith(AndroidJUnit4::class)
class CameraPoseEstimatorTest {
    @Test
    fun obliquePoseProjectsAValidBedVolume() {
        val intrinsics = CameraIntrinsics(900.0, 900.0, 640.0, 360.0, DoubleArray(5))
        val calibration = syntheticCalibration(intrinsics)

        val projection = CameraPoseEstimator()
            .projectBedVolume(calibration, intrinsics, 1280, 720)
            .getOrThrow()

        assertThat(projection.reprojectionErrorPx).isLessThan(1f)
        assertThat(projection.silhouette.points.size).isAtLeast(4)
        assertThat(projection.silhouette.area).isGreaterThan(0.05f)
        assertThat(projection.silhouette.points.all { it.x in 0f..1f && it.y in 0f..1f }).isTrue()
    }

    @Test
    fun invalidImageDimensionsAreRejected() {
        val result = CameraPoseEstimator().projectBedVolume(
            calibration = syntheticCalibration(CameraIntrinsics(900.0, 900.0, 640.0, 360.0, DoubleArray(5))),
            intrinsics = CameraIntrinsics(900.0, 900.0, 640.0, 360.0, DoubleArray(5)),
            imageWidth = 0,
            imageHeight = 720,
        )

        assertThat(result.isFailure).isTrue()
    }

    private fun syntheticCalibration(intrinsics: CameraIntrinsics): BedCalibration {
        val objectPoints = MatOfPoint3f(
            Point3(0.0, 0.0, 0.0),
            Point3(1.6, 0.0, 0.0),
            Point3(1.6, 2.0, 0.0),
            Point3(0.0, 2.0, 0.0),
        )
        val rvec = Mat(3, 1, CvType.CV_64F).apply {
            put(0, 0, 1.05)
            put(1, 0, 0.08)
            put(2, 0, -0.12)
        }
        val tvec = Mat(3, 1, CvType.CV_64F).apply {
            put(0, 0, -0.7)
            put(1, 0, -0.6)
            put(2, 0, 3.6)
        }
        val projected = MatOfPoint2f()
        Calib3d.projectPoints(
            objectPoints,
            rvec,
            tvec,
            cameraMatrix(intrinsics),
            MatOfDouble(*intrinsics.distortion),
            projected,
        )
        val corners = projected.toArray().map { point ->
            NPoint((point.x / 1280.0).toFloat(), (point.y / 720.0).toFloat())
        }
        return BedCalibration.create(
            widthMeters = 1.6f,
            lengthMeters = 2.0f,
            heightMeters = 1.4f,
            corners = corners,
            gravity = listOf(0f, 1f, 0f),
        ).getOrThrow()
    }

    private fun cameraMatrix(intrinsics: CameraIntrinsics): Mat =
        Mat.eye(3, 3, CvType.CV_64F).apply {
            put(0, 0, intrinsics.fx)
            put(1, 1, intrinsics.fy)
            put(0, 2, intrinsics.cx)
            put(1, 2, intrinsics.cy)
        }

    companion object {
        @JvmStatic
        @BeforeClass
        fun loadOpenCv() {
            assertThat(OpenCVLoader.initLocal()).isTrue()
        }
    }
}
