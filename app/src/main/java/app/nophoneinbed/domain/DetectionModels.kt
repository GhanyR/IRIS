package app.nophoneinbed.domain

enum class TrackerState { CLEAR, WATCH, ALARM, FAULT }

enum class EvidenceKind { OBJECT_MODEL, DARK_PHONE_SHAPE, LUMINOUS_SCREEN }

data class PhoneEvidence(
    val box: NRect,
    val confidence: Float,
    val kind: EvidenceKind,
    val overlapRatio: Float,
    val timestampMs: Long,
) {
    init {
        require(confidence.isFinite() && confidence in 0f..1f) { "Confidence must be between 0 and 1" }
        require(overlapRatio.isFinite() && overlapRatio in 0f..1f) { "Overlap must be between 0 and 1" }
        require(timestampMs >= 0) { "Timestamp must be non-negative" }
    }
}

data class TrackedPhone(
    val id: Int,
    val box: NRect,
    val confidence: Float,
    val confirmedByModel: Boolean,
    val lastSeenMs: Long,
    val lastKnownInside: Boolean,
    val overlapRatio: Float,
    val lastEvidenceKind: EvidenceKind,
)

data class DecisionPolicy(
    val strongConfidence: Float,
    val minimumOverlap: Float,
    val maximumEvidenceAgeMs: Long,
    val positiveFramesRequired: Int,
    val frameWindow: Int,
    val persistentPositiveMs: Long,
    val clearAfterMs: Long,
) {
    companion object {
        fun default() = DecisionPolicy(
            strongConfidence = 0.35f,
            minimumOverlap = 0.15f,
            maximumEvidenceAgeMs = 500L,
            positiveFramesRequired = 3,
            frameWindow = 5,
            persistentPositiveMs = 1_000,
            clearAfterMs = 3_000,
        )
    }
}

data class DecisionSnapshot(
    val state: TrackerState,
    val reason: String,
    val activeTracks: List<TrackedPhone>,
    val positiveFramesInWindow: Int,
)
