package app.nophoneinbed.runtime

import app.nophoneinbed.domain.TrackerState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuntimeCadencePolicyTest {
    private val policy = RuntimeCadencePolicy(
        publishIntervalMs = 1_000L,
        wakeLockTimeoutMs = 12L * 60L * 60L * 1_000L,
        wakeLockRenewalMs = 6L * 60L * 60L * 1_000L,
    )

    @Test
    fun repeatedStatePublishesAtMostOncePerSecond() {
        assertThat(policy.shouldPublish(TrackerState.CLEAR, TrackerState.CLEAR, 1_000L, 1_999L)).isFalse()
        assertThat(policy.shouldPublish(TrackerState.CLEAR, TrackerState.CLEAR, 1_000L, 2_000L)).isTrue()
    }

    @Test
    fun stateChangePublishesImmediately() {
        assertThat(policy.shouldPublish(TrackerState.CLEAR, TrackerState.ALARM, 1_000L, 1_001L)).isTrue()
        assertThat(policy.shouldPublish(null, TrackerState.CLEAR, null, 0L)).isTrue()
    }

    @Test
    fun wakeLockRenewsHalfwayBeforeItsLeaseExpires() {
        assertThat(policy.wakeLockTimeoutMs).isEqualTo(43_200_000L)
        assertThat(policy.nextWakeLockRenewalAt(5_000L)).isEqualTo(21_605_000L)
    }
}
