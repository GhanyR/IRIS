package app.nophoneinbed.domain

import kotlin.math.sqrt

@ConsistentCopyVisibility
data class BedCalibration private constructor(
    val widthMeters: Float,
    val lengthMeters: Float,
    val heightMeters: Float,
    val mattressCorners: List<NPoint>,
    val gravity: List<Float>,
    val manualUpperOffset: NPoint,
    val cameraId: String,
) {
    companion object {
        fun create(
            widthMeters: Float,
            lengthMeters: Float,
            heightMeters: Float,
            corners: List<NPoint>,
            gravity: List<Float>,
            manualUpperOffset: NPoint = NPoint(0f, 0f),
            cameraId: String = "0",
        ): Result<BedCalibration> = runCatching {
            require(widthMeters.isFinite() && widthMeters in 0.5f..3f) {
                "Bed width must be between 0.5 and 3.0 meters"
            }
            require(lengthMeters.isFinite() && lengthMeters in 0.5f..3f) {
                "Bed length must be between 0.5 and 3.0 meters"
            }
            require(heightMeters.isFinite() && heightMeters in 0.2f..2.5f) {
                "Prohibited height must be between 0.2 and 2.5 meters"
            }
            require(corners.size == 4 && corners.distinct().size == 4) {
                "Calibration needs four unique corners"
            }
            require(corners.all { it.x in 0f..1f && it.y in 0f..1f }) {
                "Calibration corners must be inside the image"
            }
            require(
                !Polygon.segmentsCross(corners[0], corners[1], corners[2], corners[3]) &&
                    !Polygon.segmentsCross(corners[1], corners[2], corners[3], corners[0]),
            ) { "Calibration corner edges cross" }
            val polygon = Polygon(corners)
            require(polygon.area >= 0.03f) { "Bed projection is too small" }
            require(polygon.isConvex()) { "Bed projection must be convex" }
            require(gravity.size == 3 && gravity.all(Float::isFinite)) {
                "Gravity vector needs three finite values"
            }
            val gravityMagnitude = sqrt(gravity.sumOf { (it * it).toDouble() }).toFloat()
            require(gravityMagnitude >= 0.1f) { "Gravity vector is too small" }
            require(cameraId.isNotBlank()) { "Camera ID is required" }

            BedCalibration(
                widthMeters = widthMeters,
                lengthMeters = lengthMeters,
                heightMeters = heightMeters,
                mattressCorners = corners.toList(),
                gravity = gravity.toList(),
                manualUpperOffset = manualUpperOffset,
                cameraId = cameraId,
            )
        }
    }
}
