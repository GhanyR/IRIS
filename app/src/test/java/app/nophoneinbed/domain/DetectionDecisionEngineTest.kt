package app.nophoneinbed.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetectionDecisionEngineTest {
    @Test
    fun entersAlarmAfterThreeOfFivePositiveFrames() {
        val engine = DetectionDecisionEngine(DecisionPolicy.default())
        val states = listOf(0L, 200L, 400L, 600L, 800L).mapIndexed { index, time ->
            engine.update(
                timestampMs = time,
                tracks = if (index in setOf(0, 2, 4)) listOf(confirmedInside(time)) else emptyList(),
                fault = null,
            ).state
        }

        assertThat(states.last()).isEqualTo(TrackerState.ALARM)
    }

    @Test
    fun continuousEvidenceForOneSecondEntersAlarm() {
        val engine = DetectionDecisionEngine(DecisionPolicy.default())

        engine.update(0, listOf(confirmedInside(0)), null)
        val result = engine.update(1_000, listOf(confirmedInside(1_000)), null)

        assertThat(result.state).isEqualTo(TrackerState.ALARM)
    }

    @Test
    fun alarmClearsOnlyAfterThreeContinuousClearSeconds() {
        val engine = DetectionDecisionEngine(DecisionPolicy.default())
        listOf(0L, 200L, 400L).forEach { time ->
            engine.update(time, listOf(confirmedInside(time)), null)
        }

        assertThat(engine.update(1_000, emptyList(), null).state).isEqualTo(TrackerState.ALARM)
        assertThat(engine.update(3_999, emptyList(), null).state).isEqualTo(TrackerState.ALARM)
        assertThat(engine.update(4_000, emptyList(), null).state).isEqualTo(TrackerState.CLEAR)
    }

    @Test
    fun staleRetainedTrackDoesNotDelayTheThreeSecondAlarmClear() {
        val engine = DetectionDecisionEngine(DecisionPolicy.default())
        listOf(0L, 200L, 400L).forEach { time ->
            engine.update(time, listOf(confirmedInside(time)), null)
        }
        val retainedTrack = confirmedInside(400)

        assertThat(engine.update(1_000, listOf(retainedTrack), null).state).isEqualTo(TrackerState.ALARM)
        assertThat(engine.update(3_999, listOf(retainedTrack), null).state).isEqualTo(TrackerState.ALARM)
        assertThat(engine.update(4_000, listOf(retainedTrack), null).state).isEqualTo(TrackerState.CLEAR)
    }

    @Test
    fun luminousScreenWithoutModelConfirmationIsWatch() {
        val engine = DetectionDecisionEngine(DecisionPolicy.default())
        val screenTrack = confirmedInside(0).copy(confirmedByModel = false)

        assertThat(engine.update(0, listOf(screenTrack), null).state).isEqualTo(TrackerState.WATCH)
    }

    @Test
    fun faultNeverReportsClear() {
        val engine = DetectionDecisionEngine(DecisionPolicy.default())

        val result = engine.update(0, emptyList(), fault = "camera unavailable")

        assertThat(result.state).isEqualTo(TrackerState.FAULT)
        assertThat(result.reason).contains("camera unavailable")
    }

    private fun confirmedInside(timestampMs: Long) = TrackedPhone(
        id = 1,
        box = NRect(.4f, .4f, .5f, .6f),
        confidence = .8f,
        confirmedByModel = true,
        lastSeenMs = timestampMs,
        lastKnownInside = true,
        overlapRatio = 1f,
        lastEvidenceKind = EvidenceKind.OBJECT_MODEL,
    )
}
