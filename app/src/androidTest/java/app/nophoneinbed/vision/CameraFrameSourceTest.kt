package app.nophoneinbed.vision

import android.Manifest
import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat

@RunWith(AndroidJUnit4::class)
class CameraFrameSourceTest {
    @get:Rule
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun opens_rear_camera_delivers_frames_and_stops() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var owner: TestLifecycleOwner
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            owner = TestLifecycleOwner(Lifecycle.State.RESUMED)
        }
        val latch = CountDownLatch(1)
        val source = CameraFrameSource(context)
        val objectDetector = MediaPipePhoneObjectDetector(context)

        source.start(owner, surfaceProvider = null) { frame ->
            assertThat(frame.bitmap.width).isGreaterThan(0)
            assertThat(frame.bitmap.height).isGreaterThan(0)
            assertThat(frame.bitmap.config).isEqualTo(Bitmap.Config.ARGB_8888)
            assertThat(OpenCVLoader.initLocal()).isTrue()
            objectDetector.detect(frame.bitmap, frame.timestampMs)
            val mat = Mat()
            try {
                Utils.bitmapToMat(frame.bitmap, mat)
                assertThat(mat.empty()).isFalse()
            } finally {
                mat.release()
            }
            latch.countDown()
            frame.close()
        }

        assertThat(latch.await(8, TimeUnit.SECONDS)).isTrue()
        source.stop()
        objectDetector.close()
        assertThat(source.isRunning).isFalse()
    }
}
