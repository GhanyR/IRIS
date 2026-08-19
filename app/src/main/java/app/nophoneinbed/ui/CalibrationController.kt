package app.nophoneinbed.ui

import app.nophoneinbed.domain.BedCalibration
import app.nophoneinbed.domain.NPoint

enum class Corner(val label: String) {
    HEAD_LEFT("kepala-kiri"),
    HEAD_RIGHT("kepala-kanan"),
    FOOT_RIGHT("kaki-kanan"),
    FOOT_LEFT("kaki-kiri"),
}

class CalibrationController {
    private val mutableCorners = mutableListOf<NPoint>()
    val corners: List<NPoint> get() = mutableCorners.toList()
    val nextPrompt: Corner? get() = Corner.entries.getOrNull(mutableCorners.size)

    fun onTap(point: NPoint): Boolean {
        if (mutableCorners.size == Corner.entries.size) return false
        mutableCorners += point
        return true
    }

    fun undo(): Boolean {
        if (mutableCorners.isEmpty()) return false
        mutableCorners.removeAt(mutableCorners.lastIndex)
        return true
    }

    fun reset() = mutableCorners.clear()

    fun build(
        widthMeters: Float,
        lengthMeters: Float,
        heightMeters: Float,
        gravity: List<Float>,
        cameraId: String,
        manualUpperOffset: NPoint = NPoint(0f, 0f),
    ): Result<BedCalibration> {
        if (mutableCorners.size != Corner.entries.size) {
            return Result.failure(IllegalStateException("Empat sudut kasur belum lengkap"))
        }
        return BedCalibration.create(
            widthMeters = widthMeters,
            lengthMeters = lengthMeters,
            heightMeters = heightMeters,
            corners = mutableCorners,
            gravity = gravity,
            manualUpperOffset = manualUpperOffset,
            cameraId = cameraId,
        )
    }
}
