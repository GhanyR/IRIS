package app.nophoneinbed.runtime

import kotlin.math.acos
import kotlin.math.sqrt

class MountMovementMonitor(
    calibratedGravity: FloatArray,
    private val maximumAngleDegrees: Float = 3f,
    private val confirmationMs: Long = 3_000L,
) {
    private val baseline = normalize(calibratedGravity)
    private var outsideToleranceSinceMs: Long? = null
    private var lastTimestampMs = -1L

    init {
        require(maximumAngleDegrees > 0f && maximumAngleDegrees < 90f)
        require(confirmationMs >= 0L)
    }

    fun isMoved(currentGravity: FloatArray, timestampMs: Long): Boolean {
        require(timestampMs >= lastTimestampMs) { "Movement timestamps must be monotonic" }
        lastTimestampMs = timestampMs
        val current = normalize(currentGravity)
        val dot = baseline.indices.sumOf { (baseline[it] * current[it]).toDouble() }.coerceIn(-1.0, 1.0)
        val angleDegrees = Math.toDegrees(acos(dot)).toFloat()
        if (angleDegrees <= maximumAngleDegrees) {
            outsideToleranceSinceMs = null
            return false
        }
        val startedAt = outsideToleranceSinceMs ?: timestampMs.also { outsideToleranceSinceMs = it }
        return timestampMs - startedAt >= confirmationMs
    }

    private fun normalize(vector: FloatArray): FloatArray {
        require(vector.size == 3 && vector.all(Float::isFinite)) { "Gravity needs three finite values" }
        val magnitude = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        require(magnitude > 0.1f) { "Gravity vector is too small" }
        return FloatArray(3) { vector[it] / magnitude }
    }
}
