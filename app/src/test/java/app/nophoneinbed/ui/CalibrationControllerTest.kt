package app.nophoneinbed.ui

import app.nophoneinbed.domain.NPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalibrationControllerTest {
    @Test
    fun calibration_uses_physical_corner_order() {
        val controller = CalibrationController()

        assertThat(controller.nextPrompt).isEqualTo(Corner.HEAD_LEFT)
        controller.onTap(NPoint(.1f, .2f))
        assertThat(controller.nextPrompt).isEqualTo(Corner.HEAD_RIGHT)
        controller.onTap(NPoint(.8f, .2f))
        assertThat(controller.nextPrompt).isEqualTo(Corner.FOOT_RIGHT)
    }

    @Test
    fun undo_and_reset_never_leave_stale_corners() {
        val controller = CalibrationController()
        controller.onTap(NPoint(.1f, .1f))
        controller.onTap(NPoint(.9f, .1f))

        assertThat(controller.undo()).isTrue()
        assertThat(controller.corners).containsExactly(NPoint(.1f, .1f))
        controller.reset()
        assertThat(controller.corners).isEmpty()
        assertThat(controller.nextPrompt).isEqualTo(Corner.HEAD_LEFT)
    }

    @Test
    fun builds_only_after_four_valid_corners_and_metadata() {
        val controller = CalibrationController()
        listOf(
            NPoint(.1f, .1f),
            NPoint(.9f, .1f),
            NPoint(.85f, .9f),
            NPoint(.15f, .9f),
        ).forEach(controller::onTap)

        val calibration = controller.build(
            widthMeters = 1.6f,
            lengthMeters = 2f,
            heightMeters = 1.4f,
            gravity = listOf(0f, 9.8f, 0f),
            cameraId = "0",
        ).getOrThrow()

        assertThat(calibration.mattressCorners).containsExactlyElementsIn(controller.corners).inOrder()
        assertThat(controller.nextPrompt).isNull()
    }

    @Test
    fun moving_a_corner_from_mac_preserves_order_and_rejects_invalid_targets() {
        val controller = CalibrationController()
        listOf(
            NPoint(.1f, .1f),
            NPoint(.9f, .1f),
            NPoint(.9f, .9f),
            NPoint(.1f, .9f),
        ).forEach(controller::onTap)

        assertThat(controller.moveCorner(1, NPoint(.82f, .18f))).isTrue()
        assertThat(controller.corners).containsExactly(
            NPoint(.1f, .1f),
            NPoint(.82f, .18f),
            NPoint(.9f, .9f),
            NPoint(.1f, .9f),
        ).inOrder()
        assertThat(controller.moveCorner(4, NPoint(.5f, .5f))).isFalse()
        assertThat(controller.moveCorner(0, NPoint(-.1f, .5f))).isFalse()
    }
}
