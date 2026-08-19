package app.nophoneinbed.runtime

import app.nophoneinbed.domain.TrackerState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlarmControllerTest {
    @Test
    fun alarm_start_and_stop_are_idempotent() {
        val output = FakeToneOutput()
        val alarm = AlarmController(output)

        alarm.apply(TrackerState.ALARM, 0L)
        alarm.apply(TrackerState.ALARM, 1L)
        assertThat(output.loopStarts).isEqualTo(1)

        alarm.apply(TrackerState.CLEAR, 2L)
        alarm.apply(TrackerState.CLEAR, 3L)
        assertThat(output.stops).isEqualTo(1)
    }

    @Test
    fun fault_tone_is_rate_limited_to_thirty_seconds() {
        val output = FakeToneOutput()
        val alarm = AlarmController(output)

        alarm.apply(TrackerState.FAULT, 0L)
        alarm.apply(TrackerState.FAULT, 29_999L)
        assertThat(output.faultTones).isEqualTo(1)
        alarm.apply(TrackerState.FAULT, 30_000L)
        assertThat(output.faultTones).isEqualTo(2)
    }

    private class FakeToneOutput : ToneOutput {
        var loopStarts = 0
        var stops = 0
        var faultTones = 0
        override fun startLoop() { loopStarts++ }
        override fun stop() { stops++ }
        override fun playFault() { faultTones++ }
        override fun close() = Unit
    }
}
