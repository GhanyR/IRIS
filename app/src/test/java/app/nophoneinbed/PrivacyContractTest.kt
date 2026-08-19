package app.nophoneinbed

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.security.MessageDigest
import org.junit.Test

class PrivacyContractTest {
    @Test
    fun manifestDoesNotRequestInternetOrPrivateMediaPermissions() {
        val xml = File("src/main/AndroidManifest.xml").readText()

        assertRemovedOrAbsent(xml, "android.permission.INTERNET")
        assertRemovedOrAbsent(xml, "android.permission.ACCESS_NETWORK_STATE")
        assertRemovedOrAbsent(xml, "android.permission.READ_MEDIA_IMAGES")
        assertRemovedOrAbsent(xml, "android.permission.READ_MEDIA_VIDEO")
        assertRemovedOrAbsent(xml, "android.permission.WRITE_EXTERNAL_STORAGE")
        assertRemovedOrAbsent(xml, "android.permission.RECORD_AUDIO")
        assertRemovedOrAbsent(xml, "android.permission.ACCESS_FINE_LOCATION")
    }

    @Test
    fun bundledDetectorMatchesReviewedModel() {
        val model = File("src/main/assets/efficientdet_lite2.tflite")
        assertThat(model.exists()).isTrue()
        val bytes = model.readBytes()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

        assertThat(hash)
            .isEqualTo("b3f50554cb0ea559e90328845f7d9ba4d13c8bff372914d24e06bc8bb72fa896")
    }

    @Test
    fun monitoringPreviewHasNoFilePersistencePath() {
        val source = File("src/main/java/app/nophoneinbed/runtime/TrackerPreviewStore.kt").readText()

        assertThat(source).doesNotContain("FileOutputStream")
        assertThat(source).doesNotContain("openFileOutput")
        assertThat(source).doesNotContain("MediaStore")
        assertThat(source).doesNotContain("getExternal")
    }

    private fun assertRemovedOrAbsent(xml: String, permission: String) {
        val declarations = Regex("<uses-permission[^>]*android:name=\"${Regex.escape(permission)}\"[^>]*/>")
            .findAll(xml)
            .map { it.value }
            .toList()
        assertThat(declarations.all { it.contains("tools:node=\"remove\"") }).isTrue()
    }
}
