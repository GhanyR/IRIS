package app.nophoneinbed.vision

import android.hardware.camera2.CameraCharacteristics
import app.nophoneinbed.domain.BedCalibration
import app.nophoneinbed.domain.BedVolumeProjection
import app.nophoneinbed.domain.NPoint
import app.nophoneinbed.domain.Polygon
import kotlin.math.hypot
import kotlin.math.sqrt
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3

data class CameraIntrinsics(
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val distortion: DoubleArray,
) {
    init {
        require(listOf(fx, fy, cx, cy).all(Double::isFinite)) { "Camera intrinsics must be finite" }
        require(fx > 0.0 && fy > 0.0) { "Camera focal lengths must be positive" }
        require(distortion.all(Double::isFinite)) { "Camera distortion must be finite" }
    }
}

data class PoseEstimate(
    val rotationVector: DoubleArray,
    val translationVector: DoubleArray,
    val reprojectionErrorPx: Float,
)

class CameraPoseEstimator {
    fun projectBedVolume(
        calibration: BedCalibration,
        intrinsics: CameraIntrinsics,
        imageWidth: Int,
        imageHeight: Int,
    ): Result<BedVolumeProjection> = runCatching {
        require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive" }

        val basePoints = mattressPoints(calibration)
        val imagePoints = MatOfPoint2f().apply {
            fromList(
                calibration.mattressCorners.map { point ->
                    org.opencv.core.Point(
                        (point.x * imageWidth).toDouble(),
                        (point.y * imageHeight).toDouble(),
                    )
                },
            )
        }
        val objectPoints = MatOfPoint3f().apply { fromList(basePoints) }
        val cameraMatrix = cameraMatrix(intrinsics)
        val distortion = MatOfDouble(*intrinsics.distortion)
        val rotationVector = Mat()
        val translationVector = Mat()

        try {
            val solved = Calib3d.solvePnP(
                objectPoints,
                imagePoints,
                cameraMatrix,
                distortion,
                rotationVector,
                translationVector,
                false,
                Calib3d.SOLVEPNP_IPPE,
            )
            require(solved) { "Camera pose could not be solved" }

            val reprojected = project(basePoints, rotationVector, translationVector, cameraMatrix, distortion)
            val error = rmsError(reprojected, imagePoints.toList())
            require(error <= MAX_REPROJECTION_ERROR_PX) {
                "Camera pose reprojection error is too large: $error px"
            }

            val rotationMatrix = Mat()
            try {
                Calib3d.Rodrigues(rotationVector, rotationMatrix)
                val topPoints = chooseTopFace(
                    basePoints = basePoints,
                    heightMeters = calibration.heightMeters.toDouble(),
                    rotationMatrix = rotationMatrix,
                    translationVector = translationVector,
                )
                val projectedBase = project(basePoints, rotationVector, translationVector, cameraMatrix, distortion)
                val projectedTop = project(topPoints, rotationVector, translationVector, cameraMatrix, distortion)
                    .map { point ->
                        org.opencv.core.Point(
                            point.x + calibration.manualUpperOffset.x * imageWidth,
                            point.y + calibration.manualUpperOffset.y * imageHeight,
                        )
                    }
                val normalized = (projectedBase + projectedTop).map { point ->
                    val x = point.x / imageWidth
                    val y = point.y / imageHeight
                    require(x.isFinite() && y.isFinite()) { "Projected bed volume is not finite" }
                    require(x in -FRAME_TOLERANCE..1.0 + FRAME_TOLERANCE) {
                        "Projected bed volume leaves the horizontal frame"
                    }
                    require(y in -FRAME_TOLERANCE..1.0 + FRAME_TOLERANCE) {
                        "Projected bed volume leaves the vertical frame"
                    }
                    NPoint(x.coerceIn(0.0, 1.0).toFloat(), y.coerceIn(0.0, 1.0).toFloat())
                }
                BedVolumeProjection(
                    silhouette = Polygon.convexHull(normalized),
                    reprojectionErrorPx = error,
                )
            } finally {
                rotationMatrix.release()
            }
        } finally {
            imagePoints.release()
            objectPoints.release()
            cameraMatrix.release()
            distortion.release()
            rotationVector.release()
            translationVector.release()
        }
    }

    private fun mattressPoints(calibration: BedCalibration): List<Point3> = listOf(
        Point3(0.0, 0.0, 0.0),
        Point3(calibration.widthMeters.toDouble(), 0.0, 0.0),
        Point3(calibration.widthMeters.toDouble(), calibration.lengthMeters.toDouble(), 0.0),
        Point3(0.0, calibration.lengthMeters.toDouble(), 0.0),
    )

    private fun chooseTopFace(
        basePoints: List<Point3>,
        heightMeters: Double,
        rotationMatrix: Mat,
        translationVector: Mat,
    ): List<Point3> {
        val baseDepth = basePoints.map { cameraDepth(it, rotationMatrix, translationVector) }.average()
        require(baseDepth > 0.0) { "Mattress is behind the camera" }

        val candidates = listOf(heightMeters, -heightMeters).map { signedHeight ->
            val points = basePoints.map { Point3(it.x, it.y, signedHeight) }
            points to points.map { cameraDepth(it, rotationMatrix, translationVector) }
        }.filter { (_, depths) -> depths.all { it > 0.0 } }

        val selected = candidates.minByOrNull { (_, depths) -> depths.average() }
            ?: error("Projected prohibited height is behind the camera")
        require(selected.second.average() < baseDepth) {
            "Could not determine the upward bed-volume direction"
        }
        return selected.first
    }

    private fun cameraDepth(point: Point3, rotation: Mat, translation: Mat): Double =
        rotation.get(2, 0)[0] * point.x +
            rotation.get(2, 1)[0] * point.y +
            rotation.get(2, 2)[0] * point.z +
            translation.get(2, 0)[0]

    private fun project(
        points: List<Point3>,
        rotationVector: Mat,
        translationVector: Mat,
        cameraMatrix: Mat,
        distortion: MatOfDouble,
    ): List<org.opencv.core.Point> {
        val source = MatOfPoint3f().apply { fromList(points) }
        val destination = MatOfPoint2f()
        return try {
            Calib3d.projectPoints(
                source,
                rotationVector,
                translationVector,
                cameraMatrix,
                distortion,
                destination,
            )
            destination.toList()
        } finally {
            source.release()
            destination.release()
        }
    }

    private fun rmsError(
        projected: List<org.opencv.core.Point>,
        observed: List<org.opencv.core.Point>,
    ): Float {
        require(projected.size == observed.size)
        val squared = projected.zip(observed).sumOf { (actual, expected) ->
            val distance = hypot(actual.x - expected.x, actual.y - expected.y)
            distance * distance
        }
        return sqrt(squared / projected.size).toFloat()
    }

    private fun cameraMatrix(intrinsics: CameraIntrinsics): Mat =
        Mat.eye(3, 3, CvType.CV_64F).apply {
            put(0, 0, intrinsics.fx)
            put(1, 1, intrinsics.fy)
            put(0, 2, intrinsics.cx)
            put(1, 2, intrinsics.cy)
        }

    companion object {
        private const val MAX_REPROJECTION_ERROR_PX = 5f
        private const val FRAME_TOLERANCE = 0.05

        fun intrinsicsFrom(
            characteristics: CameraCharacteristics,
            imageWidth: Int,
            imageHeight: Int,
        ): CameraIntrinsics {
            require(imageWidth > 0 && imageHeight > 0)
            val calibrated = characteristics[CameraCharacteristics.LENS_INTRINSIC_CALIBRATION]
            if (calibrated != null && calibrated.size >= 4) {
                return CameraIntrinsics(
                    fx = calibrated[0].toDouble(),
                    fy = calibrated[1].toDouble(),
                    cx = calibrated[2].toDouble(),
                    cy = calibrated[3].toDouble(),
                    distortion = DoubleArray(5),
                )
            }

            val focalLength = characteristics[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]
                ?.firstOrNull()
                ?: error("Camera focal length metadata is unavailable")
            val sensorSize = characteristics[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE]
                ?: error("Camera sensor-size metadata is unavailable")
            return CameraIntrinsics(
                fx = (focalLength / sensorSize.width * imageWidth).toDouble(),
                fy = (focalLength / sensorSize.height * imageHeight).toDouble(),
                cx = imageWidth / 2.0,
                cy = imageHeight / 2.0,
                distortion = DoubleArray(5),
            )
        }
    }
}
