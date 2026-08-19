package app.nophoneinbed.vision

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraFrameSourceTest {
    @get:Rule
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun opens_rear_camera_delivers_frames_and_stops() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED)
        val preview = PreviewView(context)
        val latch = CountDownLatch(1)
        val source = CameraFrameSource(context)

        source.start(owner, preview.surfaceProvider) { frame ->
            assertThat(frame.bitmap.width).isGreaterThan(0)
            assertThat(frame.bitmap.height).isGreaterThan(0)
            latch.countDown()
            frame.close()
        }

        assertThat(latch.await(8, TimeUnit.SECONDS)).isTrue()
        source.stop()
        assertThat(source.isRunning).isFalse()
    }
}
