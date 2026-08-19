package app.nophoneinbed.domain

import kotlin.math.hypot

class PhoneTrackManager(
    private val occlusionRetentionMs: Long,
    private val minimumIou: Float = 0.25f,
    private val maximumCenterDistance: Float = 0.12f,
) {
    private val tracks = linkedMapOf<Int, TrackedPhone>()
    private var nextId = 1

    init {
        require(occlusionRetentionMs >= 0) { "Occlusion retention must be non-negative" }
    }

    fun update(timestampMs: Long, evidence: List<PhoneEvidence>): List<TrackedPhone> {
        require(timestampMs >= 0) { "Timestamp must be non-negative" }
        tracks.entries.removeAll { timestampMs - it.value.lastSeenMs > occlusionRetentionMs }

        val availableTrackIds = tracks.keys.toMutableSet()
        for (item in evidence.sortedByDescending { it.confidence }) {
            val matchedId = availableTrackIds
                .mapNotNull { id ->
                    val track = tracks.getValue(id)
                    val iou = track.box.iou(item.box)
                    val distance = centerDistance(track.box, item.box)
                    if (iou >= minimumIou || distance <= maximumCenterDistance) {
                        Match(id, iou, distance)
                    } else {
                        null
                    }
                }
                .maxWithOrNull(compareBy<Match> { it.iou }.thenByDescending { -it.distance })
                ?.id

            if (matchedId == null) {
                val id = nextId++
                tracks[id] = TrackedPhone(
                    id = id,
                    box = item.box,
                    confidence = item.confidence,
                    confirmedByModel = item.kind.isPhoneConfirmation,
                    lastSeenMs = timestampMs,
                    lastKnownInside = item.overlapRatio > 0f,
                    overlapRatio = item.overlapRatio,
                    lastEvidenceKind = item.kind,
                )
            } else {
                val existing = tracks.getValue(matchedId)
                tracks[matchedId] = existing.copy(
                    box = item.box,
                    confidence = item.confidence,
                    confirmedByModel = existing.confirmedByModel || item.kind.isPhoneConfirmation,
                    lastSeenMs = timestampMs,
                    lastKnownInside = item.overlapRatio > 0f,
                    overlapRatio = item.overlapRatio,
                    lastEvidenceKind = item.kind,
                )
                availableTrackIds.remove(matchedId)
            }
        }

        return tracks.values.sortedBy { it.id }
    }

    fun clear() {
        tracks.clear()
    }

    private fun centerDistance(first: NRect, second: NRect): Float =
        hypot(first.center.x - second.center.x, first.center.y - second.center.y)

    private val EvidenceKind.isPhoneConfirmation: Boolean
        get() = this == EvidenceKind.OBJECT_MODEL || this == EvidenceKind.DARK_PHONE_SHAPE

    private data class Match(val id: Int, val iou: Float, val distance: Float)
}
