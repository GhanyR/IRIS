package app.nophoneinbed.runtime

import android.os.PowerManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThermalControllerTest {
    @Test
    fun severe_thermal_status_slows_analysis() {
        assertThat(ThermalController.intervalMs(PowerManager.THERMAL_STATUS_SEVERE)).isEqualTo(1_000L)
        assertThat(ThermalController.intervalMs(PowerManager.THERMAL_STATUS_NONE)).isEqualTo(200L)
    }

    @Test
    fun critical_status_becomes_fault_instead_of_false_clear() {
        assertThat(ThermalController.faultReason(PowerManager.THERMAL_STATUS_CRITICAL)).isNotNull()
        assertThat(ThermalController.faultReason(PowerManager.THERMAL_STATUS_MODERATE)).isNull()
    }
}
