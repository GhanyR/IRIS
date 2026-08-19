package app.nophoneinbed.data

import app.nophoneinbed.domain.BedCalibration
import app.nophoneinbed.domain.NPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalibrationStoreTest {
    @Test
    fun calibration_round_trips_without_precision_loss() {
        val storage = MemoryStorage()
        val store = CalibrationStore(storage)
        val original = validCalibration()

        store.save(original)

        assertThat(store.load("rear-main").getOrThrow()).isEqualTo(original)
    }

    @Test
    fun camera_change_requires_recalibration() {
        val store = CalibrationStore(MemoryStorage())
        store.save(validCalibration())

        assertThat(store.load("different-camera").isFailure).isTrue()
    }

    @Test
    fun unknown_version_and_non_finite_values_are_rejected() {
        val storage = MemoryStorage()
        val store = CalibrationStore(storage)
        storage.putString(CalibrationStore.KEY, "version=9")
        assertThat(store.load("rear-main").isFailure).isTrue()
        storage.putString(
            CalibrationStore.KEY,
            "version=1;width=NaN;length=2.0;height=1.4;corners=.1,.1,.9,.1,.9,.9,.1,.9;gravity=0,9.8,0;offset=0,0;camera=rear-main",
        )
        assertThat(store.load("rear-main").isFailure).isTrue()
    }

    private fun validCalibration() = BedCalibration.create(
        widthMeters = 1.6f,
        lengthMeters = 2f,
        heightMeters = 1.4f,
        corners = listOf(NPoint(.1f, .1f), NPoint(.9f, .1f), NPoint(.85f, .9f), NPoint(.15f, .9f)),
        gravity = listOf(0f, 9.80665f, .03f),
        manualUpperOffset = NPoint(.01f, -.02f),
        cameraId = "rear-main",
    ).getOrThrow()

    private class MemoryStorage : CalibrationStorage {
        private val values = mutableMapOf<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
}
