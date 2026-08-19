package app.nophoneinbed

import android.Manifest
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.nophoneinbed.data.CalibrationStore
import app.nophoneinbed.data.TrackingStateStore
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @Before
    fun clearCalibration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CalibrationStore(context).clear()
        TrackingStateStore(context).setArmed(false)
    }

    @Test
    fun launch_without_calibration_shows_calibrate_action() {
        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertThat(activity.findViewById<View>(R.id.calibrateButton).visibility).isEqualTo(View.VISIBLE)
                assertThat(activity.findViewById<View>(R.id.startButton).isEnabled).isFalse()
            }
        }
    }
}
