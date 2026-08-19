package app.nophoneinbed.vision

import app.nophoneinbed.domain.NPoint

enum class PreviewScaleMode { FILL_CENTER, FIT_CENTER }

/** Maps normalized analysis coordinates to PreviewView's center-cropped coordinates. */
class CoordinateMapper(
    private val analysisWidth: Int,
    private val analysisHeight: Int,
    private val previewWidth: Int,
    private val previewHeight: Int,
    rotationDegrees: Int,
    private val mirrored: Boolean = false,
    scaleMode: PreviewScaleMode = PreviewScaleMode.FILL_CENTER,
) {
    private val rotation = ((rotationDegrees % 360) + 360) % 360
    private val rotatedWidth = if (rotation == 90 || rotation == 270) analysisHeight else analysisWidth
    private val rotatedHeight = if (rotation == 90 || rotation == 270) analysisWidth else analysisHeight
    private val scale = when (scaleMode) {
        PreviewScaleMode.FILL_CENTER -> maxOf(
            previewWidth.toFloat() / rotatedWidth,
            previewHeight.toFloat() / rotatedHeight,
        )
        PreviewScaleMode.FIT_CENTER -> minOf(
            previewWidth.toFloat() / rotatedWidth,
            previewHeight.toFloat() / rotatedHeight,
        )
    }
    private val displayedWidth = rotatedWidth * scale
    private val displayedHeight = rotatedHeight * scale
    private val offsetX = (previewWidth - displayedWidth) / 2f
    private val offsetY = (previewHeight - displayedHeight) / 2f

    init {
        require(analysisWidth > 0 && analysisHeight > 0 && previewWidth > 0 && previewHeight > 0)
        require(rotation in setOf(0, 90, 180, 270)) { "Rotation must be 0, 90, 180, or 270" }
    }

    fun toPreview(point: NPoint): NPoint {
        val rotated = rotate(point)
        val x = if (mirrored) 1f - rotated.x else rotated.x
        return NPoint(
            (x * displayedWidth + offsetX) / previewWidth,
            (rotated.y * displayedHeight + offsetY) / previewHeight,
        )
    }

    fun toAnalysis(point: NPoint): NPoint {
        var x = (point.x * previewWidth - offsetX) / displayedWidth
        val y = (point.y * previewHeight - offsetY) / displayedHeight
        if (mirrored) x = 1f - x
        return inverseRotate(NPoint(x, y))
    }

    private fun rotate(point: NPoint): NPoint = when (rotation) {
        0 -> point
        90 -> NPoint(1f - point.y, point.x)
        180 -> NPoint(1f - point.x, 1f - point.y)
        270 -> NPoint(point.y, 1f - point.x)
        else -> error("Unsupported rotation")
    }

    private fun inverseRotate(point: NPoint): NPoint = when (rotation) {
        0 -> point
        90 -> NPoint(point.y, 1f - point.x)
        180 -> NPoint(1f - point.x, 1f - point.y)
        270 -> NPoint(1f - point.y, point.x)
        else -> error("Unsupported rotation")
    }
}
