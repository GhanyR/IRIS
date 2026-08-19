package app.nophoneinbed.vision

import app.nophoneinbed.domain.NPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoordinateMapperTest {
    @Test
    fun maps_rotated_analysis_box_to_preview() {
        val mapper = CoordinateMapper(
            analysisWidth = 1280,
            analysisHeight = 720,
            previewWidth = 720,
            previewHeight = 1280,
            rotationDegrees = 90,
        )

        assertThat(mapper.toPreview(NPoint(0f, 0f))).isEqualTo(NPoint(1f, 0f))
        assertThat(mapper.toPreview(NPoint(1f, 1f))).isEqualTo(NPoint(0f, 1f))
    }

    @Test
    fun mirrored_preview_flips_horizontal_axis_after_rotation() {
        val mapper = CoordinateMapper(640, 480, 640, 480, rotationDegrees = 0, mirrored = true)

        assertThat(mapper.toPreview(NPoint(.2f, .4f))).isEqualTo(NPoint(.8f, .4f))
    }

    @Test
    fun center_crop_round_trips_visible_point() {
        val mapper = CoordinateMapper(1280, 720, 1000, 1000, rotationDegrees = 0)
        val source = NPoint(.5f, .5f)

        assertThat(mapper.toAnalysis(mapper.toPreview(source)).x).isWithin(.0001f).of(source.x)
        assertThat(mapper.toAnalysis(mapper.toPreview(source)).y).isWithin(.0001f).of(source.y)
    }
}
