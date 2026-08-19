package app.nophoneinbed

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.security.MessageDigest
import org.junit.Test

class PrivacyContractTest {
    @Test
    fun manifestDoesNotRequestInternetOrPrivateMediaPermissions() {
        val xml = File("src/main/AndroidManifest.xml").readText()

        assertThat(xml).doesNotContain("android.permission.INTERNET")
        assertThat(xml).doesNotContain("READ_MEDIA")
        assertThat(xml).doesNotContain("WRITE_EXTERNAL_STORAGE")
        assertThat(xml).doesNotContain("RECORD_AUDIO")
        assertThat(xml).doesNotContain("ACCESS_FINE_LOCATION")
    }

    @Test
    fun bundledDetectorMatchesReviewedModel() {
        val model = File("src/main/assets/efficientdet_lite0.tflite")
        assertThat(model.exists()).isTrue()
        val bytes = model.readBytes()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

        assertThat(hash)
            .isEqualTo("0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb")
    }
}
