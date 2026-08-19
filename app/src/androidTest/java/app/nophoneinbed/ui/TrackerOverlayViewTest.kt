package app.nophoneinbed.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nophoneinbed.domain.NPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class TrackerOverlayViewTest {
    @Test
    fun nearby_tap_still_adds_next_corner_until_four_are_complete() {
        var dragged = false
        var tappedPoint: NPoint? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = TrackerOverlayView(ApplicationProvider.getApplicationContext())
            view.layout(0, 0, 1_000, 1_000)
            view.render(
                OverlayRenderState(
                    calibrationCorners = listOf(
                        NPoint(.2f, .2f),
                        NPoint(.8f, .2f),
                        NPoint(.8f, .8f),
                    ),
                ),
            )
            view.calibrationEditable = true
            view.onNormalizedTap = { tappedPoint = it }
            view.onNormalizedDrag = { _, _ -> dragged = true }

            val downTime = SystemClock.uptimeMillis()
            view.onTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 220f, 220f, 0))
            view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_UP, 220f, 220f, 0))
        }

        assertThat(tappedPoint?.x).isWithin(.001f).of(.22f)
        assertThat(dragged).isFalse()
    }

    @Test
    fun dragging_existing_corner_moves_it_without_adding_a_new_tap() {
        var draggedIndex: Int? = null
        var draggedPoint: NPoint? = null
        var tapCount = 0

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = TrackerOverlayView(ApplicationProvider.getApplicationContext())
            view.layout(0, 0, 1_000, 1_000)
            view.render(
                OverlayRenderState(
                    calibrationCorners = listOf(
                        NPoint(.2f, .2f),
                        NPoint(.8f, .2f),
                        NPoint(.8f, .8f),
                        NPoint(.2f, .8f),
                    ),
                ),
            )
            view.calibrationEditable = true
            view.onNormalizedTap = { tapCount += 1 }
            view.onNormalizedDrag = { index, point ->
                draggedIndex = index
                draggedPoint = point
            }

            val downTime = SystemClock.uptimeMillis()
            view.onTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 200f, 0))
            view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_MOVE, 320f, 360f, 0))
            view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 20, MotionEvent.ACTION_UP, 320f, 360f, 0))
        }

        assertThat(draggedIndex).isEqualTo(0)
        assertThat(draggedPoint?.x).isWithin(.001f).of(.32f)
        assertThat(draggedPoint?.y).isWithin(.001f).of(.36f)
        assertThat(tapCount).isEqualTo(0)
    }
}
