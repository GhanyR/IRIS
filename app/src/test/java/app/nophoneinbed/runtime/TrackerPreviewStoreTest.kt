package app.nophoneinbed.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackerPreviewStoreTest {
    @Test
    fun previewEncodingIsLimitedToTwoFramesPerSecond() {
        val store = TrackerPreviewStore(minimumIntervalMs = 500L)
        store.setConsumerActive(true)
        var encodes = 0

        assertThat(store.offer(1_000L) { encodes++; byteArrayOf(1) }).isTrue()
        assertThat(store.offer(1_499L) { encodes++; byteArrayOf(2) }).isFalse()
        assertThat(store.offer(1_500L) { encodes++; byteArrayOf(3) }).isTrue()

        assertThat(encodes).isEqualTo(2)
        assertThat(store.frames.value?.jpegBytes()?.toList()).isEqualTo(listOf(3.toByte()))
    }

    @Test
    fun previewFramesDefensivelyCopyEncodedBytes() {
        val store = TrackerPreviewStore(minimumIntervalMs = 500L)
        store.setConsumerActive(true)
        val source = byteArrayOf(4, 5, 6)
        store.offer(0L) { source }

        source[0] = 9
        val firstRead = store.frames.value!!.jpegBytes()
        firstRead[1] = 9

        assertThat(store.frames.value!!.jpegBytes().toList())
            .isEqualTo(listOf(4.toByte(), 5.toByte(), 6.toByte()))
    }

    @Test
    fun clearRemovesTheLastInMemoryPreview() {
        val store = TrackerPreviewStore(minimumIntervalMs = 500L)
        store.setConsumerActive(true)
        store.offer(0L) { byteArrayOf(1) }

        store.clear()

        assertThat(store.frames.value).isNull()
    }

    @Test
    fun noPreviewIsEncodedWhileTheActivityHasNoConsumer() {
        val store = TrackerPreviewStore(minimumIntervalMs = 500L)
        var encodes = 0

        assertThat(store.offer(0L) { encodes++; byteArrayOf(1) }).isFalse()
        store.setConsumerActive(true)
        assertThat(store.offer(1L) { encodes++; byteArrayOf(2) }).isTrue()
        store.setConsumerActive(false)

        assertThat(encodes).isEqualTo(1)
        assertThat(store.frames.value).isNull()
    }
}
