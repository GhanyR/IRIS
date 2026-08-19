package app.nophoneinbed.runtime

import kotlin.math.min

class CameraHealthPolicy(
    private val frameTimeoutMs: Long,
    private val retryDelaysMs: LongArray = longArrayOf(
        1_000L,
        2_000L,
        4_000L,
        8_000L,
        15_000L,
        30_000L,
        60_000L,
    ),
) {
    @Volatile private var boundAtMs: Long? = null
    @Volatile private var lastFrameAtMs: Long? = null

    init {
        require(frameTimeoutMs > 0L)
        require(retryDelaysMs.isNotEmpty() && retryDelaysMs.all { it > 0L })
    }

    fun onBound(nowMs: Long) {
        require(nowMs >= 0L)
        boundAtMs = nowMs
        lastFrameAtMs = null
    }

    fun onFrame(nowMs: Long) {
        require(nowMs >= 0L)
        lastFrameAtMs = nowMs
    }

    fun isStalled(nowMs: Long): Boolean {
        require(nowMs >= 0L)
        val referenceMs = lastFrameAtMs ?: boundAtMs ?: return false
        return nowMs - referenceMs >= frameTimeoutMs
    }

    fun retryDelayMs(attempt: Int): Long {
        require(attempt >= 0)
        return retryDelaysMs[min(attempt, retryDelaysMs.lastIndex)]
    }
}
