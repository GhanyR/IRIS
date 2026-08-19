package app.nophoneinbed.domain

data class BedVolumeProjection(
    val silhouette: Polygon,
    val reprojectionErrorPx: Float,
) {
    init {
        require(reprojectionErrorPx.isFinite() && reprojectionErrorPx >= 0f) {
            "Reprojection error must be finite and non-negative"
        }
    }
}

class BedVolumeModel(private val projection: BedVolumeProjection) {
    fun overlapRatio(rect: NRect): Float =
        (projection.silhouette.intersectionArea(rect) / rect.area).coerceIn(0f, 1f)

    fun containsCenter(rect: NRect): Boolean = projection.silhouette.contains(rect.center)
}
