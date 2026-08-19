package app.nophoneinbed

import android.Manifest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.nophoneinbed.data.CalibrationStore
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val activity = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun clearCalibration() {
        CalibrationStore(InstrumentationRegistry.getInstrumentation().targetContext).clear()
        activity.scenario.recreate()
    }

    @Test
    fun launch_without_calibration_shows_calibrate_action() {
        onView(withId(R.id.calibrateButton)).check(matches(isDisplayed()))
        onView(withId(R.id.startButton)).check(matches(not(isEnabled())))
    }
}
