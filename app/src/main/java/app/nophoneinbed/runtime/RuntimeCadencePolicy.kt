package app.nophoneinbed.runtime

import app.nophoneinbed.domain.TrackerState

class RuntimeCadencePolicy(
    private val publishIntervalMs: Long,
    val wakeLockTimeoutMs: Long,
    private val wakeLockRenewalMs: Long,
) {
    init {
        require(publishIntervalMs > 0L)
        require(wakeLockTimeoutMs > 0L)
        require(wakeLockRenewalMs in 1 until wakeLockTimeoutMs)
    }

    fun shouldPublish(
        previousState: TrackerState?,
        nextState: TrackerState,
        lastPublishMs: Long?,
        nowMs: Long,
    ): Boolean {
        require(nowMs >= 0L)
        return previousState != nextState || lastPublishMs == null || nowMs - lastPublishMs >= publishIntervalMs
    }

    fun nextWakeLockRenewalAt(acquiredAtMs: Long): Long {
        require(acquiredAtMs >= 0L)
        return acquiredAtMs + wakeLockRenewalMs
    }
}
