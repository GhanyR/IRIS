package app.nophoneinbed.vision

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaPipePhoneObjectDetectorTest {
    @Test
    fun detectorLoadsReviewedModelAndAllowsOnlyCellPhone() {
        MediaPipePhoneObjectDetector(ApplicationProvider.getApplicationContext()).use { detector ->
            assertThat(detector.categoryAllowlist).containsExactly("cell phone")
            val blank = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
            assertThat(detector.detect(blank, timestampMs = 10)).isEmpty()
            assertThat(blank.isRecycled).isFalse()
            blank.recycle()
        }
    }
}
