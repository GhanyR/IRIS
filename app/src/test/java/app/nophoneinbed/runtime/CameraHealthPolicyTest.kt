package app.nophoneinbed.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CameraHealthPolicyTest {
    @Test
    fun cameraWithoutFirstFrameStallsAfterTenSeconds() {
        val health = CameraHealthPolicy(frameTimeoutMs = 10_000L)

        health.onBound(1_000L)

        assertThat(health.isStalled(10_999L)).isFalse()
        assertThat(health.isStalled(11_000L)).isTrue()
    }

    @Test
    fun eachFreshFrameResetsTheStallDeadline() {
        val health = CameraHealthPolicy(frameTimeoutMs = 10_000L)
        health.onBound(0L)
        health.onFrame(9_000L)

        assertThat(health.isStalled(18_999L)).isFalse()
        assertThat(health.isStalled(19_000L)).isTrue()
    }

    @Test
    fun retryDelayGrowsAndCapsAtSixtySecondsWithoutGivingUp() {
        val health = CameraHealthPolicy(frameTimeoutMs = 10_000L)

        assertThat((0..8).map(health::retryDelayMs))
            .containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L, 60_000L, 60_000L, 60_000L)
            .inOrder()
    }
}
