package app.nophoneinbed.runtime

import android.os.PowerManager

object ThermalController {
    fun intervalMs(status: Int): Long = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> 200L
        PowerManager.THERMAL_STATUS_LIGHT -> 300L
        PowerManager.THERMAL_STATUS_MODERATE -> 500L
        PowerManager.THERMAL_STATUS_SEVERE -> 1_000L
        else -> 1_000L
    }

    fun faultReason(status: Int): String? = when (status) {
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN,
        -> "Perangkat terlalu panas; analisis dihentikan sementara"
        else -> null
    }
}
