package app.nophoneinbed.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MountMovementMonitorTest {
    @Test
    fun mount_rotation_above_three_degrees_invalidates_calibration() {
        val monitor = MountMovementMonitor(floatArrayOf(0f, 1f, 0f), maximumAngleDegrees = 3f)

        assertThat(monitor.isMoved(floatArrayOf(0f, .9986f, .0523f))).isFalse()
        assertThat(monitor.isMoved(floatArrayOf(0f, .9962f, .0872f))).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_zero_gravity_vector() {
        MountMovementMonitor(floatArrayOf(0f, 0f, 0f))
    }
}
