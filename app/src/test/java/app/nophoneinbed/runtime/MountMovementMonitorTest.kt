package app.nophoneinbed.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MountMovementMonitorTest {
    @Test
    fun mount_rotation_must_persist_for_three_seconds_before_invalidating_calibration() {
        val monitor = MountMovementMonitor(
            floatArrayOf(0f, 1f, 0f),
            maximumAngleDegrees = 3f,
            confirmationMs = 3_000L,
        )

        assertThat(monitor.isMoved(floatArrayOf(0f, .9986f, .0523f), 0L)).isFalse()
        assertThat(monitor.isMoved(floatArrayOf(0f, .9962f, .0872f), 1_000L)).isFalse()
        assertThat(monitor.isMoved(floatArrayOf(0f, .9962f, .0872f), 3_999L)).isFalse()
        assertThat(monitor.isMoved(floatArrayOf(0f, .9962f, .0872f), 4_000L)).isTrue()
    }

    @Test
    fun transient_bump_resets_the_movement_confirmation_window() {
        val monitor = MountMovementMonitor(
            floatArrayOf(0f, 1f, 0f),
            maximumAngleDegrees = 3f,
            confirmationMs = 3_000L,
        )

        assertThat(monitor.isMoved(floatArrayOf(0f, .9962f, .0872f), 1_000L)).isFalse()
        assertThat(monitor.isMoved(floatArrayOf(0f, 1f, 0f), 2_000L)).isFalse()
        assertThat(monitor.isMoved(floatArrayOf(0f, .9962f, .0872f), 3_000L)).isFalse()
        assertThat(monitor.isMoved(floatArrayOf(0f, .9962f, .0872f), 5_999L)).isFalse()
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_zero_gravity_vector() {
        MountMovementMonitor(floatArrayOf(0f, 0f, 0f))
    }
}
