package app.nophoneinbed.runtime

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.core.content.ContextCompat
import app.nophoneinbed.data.CalibrationStore
import app.nophoneinbed.domain.TrackerState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerForegroundServiceTest {
    @get:Rule(order = 0)
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun missing_calibration_stays_fault_instead_of_reporting_clear() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CalibrationStore(context).clear()

        ContextCompat.startForegroundService(context, TrackerForegroundService.startIntent(context))

        val deadline = System.currentTimeMillis() + 5_000L
        while (
            TrackerRuntime.statusStore.status.value.faultReason?.contains("belum dikalibrasi") != true &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(50L)
        }
        val snapshot = TrackerRuntime.statusStore.status.value
        assertThat(snapshot.state).isEqualTo(TrackerState.FAULT)
        assertThat(snapshot.faultReason).contains("belum dikalibrasi")
        context.startService(TrackerForegroundService.stopIntent(context))
    }
}
