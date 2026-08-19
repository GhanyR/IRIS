package app.nophoneinbed.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryPreview private constructor(private val jpeg: ByteArray) {
    fun jpegBytes(): ByteArray = jpeg.copyOf()

    companion object {
        fun from(jpeg: ByteArray) = InMemoryPreview(jpeg.copyOf())
    }
}

class TrackerPreviewStore(private val minimumIntervalMs: Long) {
    private val mutableFrames = MutableStateFlow<InMemoryPreview?>(null)
    val frames: StateFlow<InMemoryPreview?> = mutableFrames.asStateFlow()
    private var lastAcceptedAtMs: Long? = null
    private var consumerActive = false

    init {
        require(minimumIntervalMs > 0L)
    }

    @Synchronized
    fun offer(nowMs: Long, encode: () -> ByteArray): Boolean {
        require(nowMs >= 0L)
        if (!consumerActive) return false
        val previous = lastAcceptedAtMs
        if (previous != null && nowMs - previous < minimumIntervalMs) return false
        val encoded = encode()
        require(encoded.isNotEmpty()) { "Preview JPEG must not be empty" }
        mutableFrames.value = InMemoryPreview.from(encoded)
        lastAcceptedAtMs = nowMs
        return true
    }

    @Synchronized
    fun clear() {
        mutableFrames.value = null
        lastAcceptedAtMs = null
    }

    @Synchronized
    fun setConsumerActive(active: Boolean) {
        consumerActive = active
        if (!active) {
            mutableFrames.value = null
            lastAcceptedAtMs = null
        }
    }
}
