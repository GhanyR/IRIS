package app.nophoneinbed

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestPrivacyTest {
    @Test
    fun installed_manifest_has_no_internet_or_media_permissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val requested = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        ).requestedPermissions?.toList().orEmpty()

        assertThat(requested).doesNotContain(Manifest.permission.INTERNET)
        assertThat(requested).doesNotContain(Manifest.permission.RECORD_AUDIO)
        assertThat(requested).doesNotContain("android.permission.READ_MEDIA_IMAGES")
        assertThat(requested).doesNotContain("android.permission.READ_MEDIA_VIDEO")
        assertThat(requested).doesNotContain(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
