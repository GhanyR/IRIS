package app.nophoneinbed.vision

import android.graphics.Bitmap
import app.nophoneinbed.domain.PhoneEvidence

interface PhoneObjectDetector : AutoCloseable {
    val categoryAllowlist: Set<String>
    fun detect(bitmap: Bitmap, timestampMs: Long): List<PhoneEvidence>
}
