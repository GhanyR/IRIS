package app.nophoneinbed.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalibrationTest {
    @Test
    fun selfCrossingCornersAreRejected() {
        val result = BedCalibration.create(
            widthMeters = 1.6f,
            lengthMeters = 2.0f,
            heightMeters = 1.4f,
            corners = listOf(
                NPoint(0.1f, 0.1f),
                NPoint(0.9f, 0.9f),
                NPoint(0.9f, 0.1f),
                NPoint(0.1f, 0.9f),
            ),
            gravity = listOf(0f, 1f, 0f),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("cross")
    }

    @Test
    fun obliqueSideViewTrapezoidIsAccepted() {
        val result = BedCalibration.create(
            widthMeters = 1.6f,
            lengthMeters = 2.0f,
            heightMeters = 1.4f,
            corners = listOf(
                NPoint(0.35f, 0.30f),
                NPoint(0.70f, 0.34f),
                NPoint(0.92f, 0.82f),
                NPoint(0.08f, 0.78f),
            ),
            gravity = listOf(0.1f, 0.9f, 0.2f),
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().mattressCorners).hasSize(4)
    }

    @Test
    fun tinyBedProjectionIsRejected() {
        val result = BedCalibration.create(
            widthMeters = 1.6f,
            lengthMeters = 2.0f,
            heightMeters = 1.4f,
            corners = listOf(
                NPoint(0.49f, 0.49f),
                NPoint(0.51f, 0.49f),
                NPoint(0.51f, 0.51f),
                NPoint(0.49f, 0.51f),
            ),
            gravity = listOf(0f, 1f, 0f),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("small")
    }
}
