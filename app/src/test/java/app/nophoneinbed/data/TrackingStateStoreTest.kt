package app.nophoneinbed.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackingStateStoreTest {
    @Test
    fun armedStateSurvivesAcrossStoreInstancesUntilExplicitStop() {
        val storage = MemoryTrackingStateStorage()
        val first = TrackingStateStore(storage)
        first.setArmed(true)

        assertThat(TrackingStateStore(storage).isArmed()).isTrue()

        TrackingStateStore(storage).setArmed(false)
        assertThat(first.isArmed()).isFalse()
    }

    private class MemoryTrackingStateStorage : TrackingStateStorage {
        private var armed = false
        override fun getArmed(): Boolean = armed
        override fun putArmed(value: Boolean) {
            armed = value
        }
    }
}
