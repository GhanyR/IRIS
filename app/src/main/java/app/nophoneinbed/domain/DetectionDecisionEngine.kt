package app.nophoneinbed.domain

import java.util.ArrayDeque

class DetectionDecisionEngine(private val policy: DecisionPolicy) {
    private val positiveFrames = ArrayDeque<Boolean>()
    private var state = TrackerState.CLEAR
    private var continuousPositiveSinceMs: Long? = null
    private var clearSinceMs: Long? = null
    private var lastTimestampMs = -1L

    init {
        require(policy.strongConfidence in 0f..1f)
        require(policy.minimumOverlap in 0f..1f)
        require(policy.positiveFramesRequired in 1..policy.frameWindow)
        require(policy.persistentPositiveMs >= 0)
        require(policy.clearAfterMs >= 0)
    }

    fun update(
        timestampMs: Long,
        tracks: List<TrackedPhone>,
        fault: String?,
    ): DecisionSnapshot {
        require(timestampMs >= lastTimestampMs) { "Decision timestamps must be monotonic" }
        lastTimestampMs = timestampMs

        if (fault != null) {
            state = TrackerState.FAULT
            continuousPositiveSinceMs = null
            clearSinceMs = null
            return snapshot(tracks, "Fault: $fault")
        }

        val confirmedInside = tracks.filter {
            it.confirmedByModel && it.lastKnownInside && it.overlapRatio >= policy.minimumOverlap
        }
        val strongPositive = confirmedInside.any { it.confidence >= policy.strongConfidence }
        val watchEvidence = tracks.any { it.lastKnownInside && it.overlapRatio > 0f }

        positiveFrames.addLast(strongPositive)
        while (positiveFrames.size > policy.frameWindow) positiveFrames.removeFirst()

        if (strongPositive) {
            if (continuousPositiveSinceMs == null) continuousPositiveSinceMs = timestampMs
        } else {
            continuousPositiveSinceMs = null
        }

        val positiveCount = positiveFrames.count { it }
        val persistentPositive = continuousPositiveSinceMs?.let {
            timestampMs - it >= policy.persistentPositiveMs
        } == true
        val alarmThresholdReached = positiveCount >= policy.positiveFramesRequired || persistentPositive

        state = when {
            state == TrackerState.ALARM -> updateExistingAlarm(timestampMs, confirmedInside.isNotEmpty())
            alarmThresholdReached -> {
                clearSinceMs = null
                TrackerState.ALARM
            }
            watchEvidence || positiveCount > 0 -> TrackerState.WATCH
            else -> TrackerState.CLEAR
        }

        val reason = when (state) {
            TrackerState.CLEAR -> "No phone evidence in the bed volume"
            TrackerState.WATCH -> "Phone-like evidence is not yet confirmed"
            TrackerState.ALARM -> "Confirmed phone intersects the bed volume"
            TrackerState.FAULT -> error("Fault is returned before decision processing")
        }
        return snapshot(tracks, reason)
    }

    fun reset() {
        positiveFrames.clear()
        state = TrackerState.CLEAR
        continuousPositiveSinceMs = null
        clearSinceMs = null
        lastTimestampMs = -1L
    }

    private fun updateExistingAlarm(timestampMs: Long, confirmedInside: Boolean): TrackerState {
        if (confirmedInside) {
            clearSinceMs = null
            return TrackerState.ALARM
        }
        val clearStart = clearSinceMs ?: timestampMs.also { clearSinceMs = it }
        return if (timestampMs - clearStart >= policy.clearAfterMs) {
            clearSinceMs = null
            positiveFrames.clear()
            TrackerState.CLEAR
        } else {
            TrackerState.ALARM
        }
    }

    private fun snapshot(tracks: List<TrackedPhone>, reason: String) = DecisionSnapshot(
        state = state,
        reason = reason,
        activeTracks = tracks.toList(),
        positiveFramesInWindow = positiveFrames.count { it },
    )
}
