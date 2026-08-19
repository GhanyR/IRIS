package app.nophoneinbed.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneTrackManagerTest {
    @Test
    fun confirmedPhoneSurvivesTenSecondOcclusion() {
        val manager = PhoneTrackManager(occlusionRetentionMs = 10_000)
        manager.update(0, listOf(modelEvidence(timestampMs = 0)))

        assertThat(manager.update(9_999, emptyList()).single().lastKnownInside).isTrue()
        assertThat(manager.update(10_001, emptyList())).isEmpty()
    }

    @Test
    fun nearbyDetectionsKeepTheSameTrackId() {
        val manager = PhoneTrackManager(occlusionRetentionMs = 10_000)
        val first = manager.update(0, listOf(modelEvidence(box = NRect(.4f, .4f, .5f, .6f)))).single()
        val second = manager.update(200, listOf(modelEvidence(box = NRect(.42f, .41f, .52f, .61f)))).single()

        assertThat(second.id).isEqualTo(first.id)
        assertThat(second.lastSeenMs).isEqualTo(200)
    }

    @Test
    fun luminousRectangleCannotConfirmANewPhoneByItself() {
        val manager = PhoneTrackManager(occlusionRetentionMs = 10_000)

        val track = manager.update(
            0,
            listOf(
                PhoneEvidence(
                    box = NRect(.4f, .4f, .5f, .6f),
                    confidence = .30f,
                    kind = EvidenceKind.LUMINOUS_SCREEN,
                    overlapRatio = 1f,
                    timestampMs = 0,
                ),
            ),
        ).single()

        assertThat(track.confirmedByModel).isFalse()
        assertThat(track.lastKnownInside).isTrue()
    }

    private fun modelEvidence(
        box: NRect = NRect(.4f, .4f, .5f, .6f),
        timestampMs: Long = 0,
    ) = PhoneEvidence(
        box = box,
        confidence = .8f,
        kind = EvidenceKind.OBJECT_MODEL,
        overlapRatio = 1f,
        timestampMs = timestampMs,
    )
}
