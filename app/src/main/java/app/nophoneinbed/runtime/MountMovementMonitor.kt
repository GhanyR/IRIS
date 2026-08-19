package app.nophoneinbed.runtime

import kotlin.math.acos
import kotlin.math.sqrt

class MountMovementMonitor(
    calibratedGravity: FloatArray,
    private val maximumAngleDegrees: Float = 3f,
) {
    private val baseline = normalize(calibratedGravity)

    init {
        require(maximumAngleDegrees > 0f && maximumAngleDegrees < 90f)
    }

    fun isMoved(currentGravity: FloatArray): Boolean {
        val current = normalize(currentGravity)
        val dot = baseline.indices.sumOf { (baseline[it] * current[it]).toDouble() }.coerceIn(-1.0, 1.0)
        val angleDegrees = Math.toDegrees(acos(dot)).toFloat()
        return angleDegrees > maximumAngleDegrees
    }

    private fun normalize(vector: FloatArray): FloatArray {
        require(vector.size == 3 && vector.all(Float::isFinite)) { "Gravity needs three finite values" }
        val magnitude = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        require(magnitude > 0.1f) { "Gravity vector is too small" }
        return FloatArray(3) { vector[it] / magnitude }
    }
}
