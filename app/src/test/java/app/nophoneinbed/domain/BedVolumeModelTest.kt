package app.nophoneinbed.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BedVolumeModelTest {
    @Test
    fun phoneBoxInsideProjectedPrismHasFullOverlap() {
        val model = BedVolumeModel(
            BedVolumeProjection(
                silhouette = Polygon.rectangle(0.2f, 0.2f, 0.8f, 0.85f),
                reprojectionErrorPx = 1.2f,
            ),
        )

        assertThat(model.overlapRatio(NRect(0.45f, 0.40f, 0.55f, 0.60f)))
            .isWithin(0.0001f)
            .of(1f)
    }

    @Test
    fun phoneBoxOutsideProjectedPrismHasNoOverlap() {
        val model = BedVolumeModel(
            BedVolumeProjection(
                silhouette = Polygon.rectangle(0.2f, 0.2f, 0.8f, 0.85f),
                reprojectionErrorPx = 1.2f,
            ),
        )

        assertThat(model.overlapRatio(NRect(0.82f, 0.40f, 0.92f, 0.60f))).isEqualTo(0f)
        assertThat(model.containsCenter(NRect(0.82f, 0.40f, 0.92f, 0.60f))).isFalse()
    }

    @Test
    fun partialPhoneOverlapReturnsFractionOfPhoneArea() {
        val model = BedVolumeModel(
            BedVolumeProjection(
                silhouette = Polygon.rectangle(0.2f, 0.2f, 0.8f, 0.8f),
                reprojectionErrorPx = 1f,
            ),
        )

        assertThat(model.overlapRatio(NRect(0.7f, 0.4f, 0.9f, 0.6f)))
            .isWithin(0.0001f)
            .of(0.5f)
    }
}
