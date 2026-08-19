package app.nophoneinbed.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import app.nophoneinbed.domain.NPoint
import app.nophoneinbed.domain.PhoneEvidence
import app.nophoneinbed.domain.Polygon
import app.nophoneinbed.domain.TrackerState
import app.nophoneinbed.vision.CoordinateMapper

data class OverlayRenderState(
    val calibrationCorners: List<NPoint> = emptyList(),
    val projectedVolume: Polygon? = null,
    val detections: List<PhoneEvidence> = emptyList(),
    val trackerState: TrackerState = TrackerState.CLEAR,
)

class TrackerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var state = OverlayRenderState()
    var coordinateMapper: CoordinateMapper? = null
    var onNormalizedTap: ((NPoint) -> Unit)? = null

    fun render(newState: OverlayRenderState) {
        state = newState
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        linePaint.color = when (state.trackerState) {
            TrackerState.ALARM -> Color.RED
            TrackerState.WATCH -> Color.YELLOW
            TrackerState.FAULT -> Color.MAGENTA
            TrackerState.CLEAR -> Color.GREEN
        }
        state.projectedVolume?.let { drawPolygon(canvas, it) }
        state.calibrationCorners.forEachIndexed { index, point ->
            val displayPoint = map(point)
            fillPaint.color = Color.CYAN
            canvas.drawCircle(displayPoint.x * width, displayPoint.y * height, 9f * resources.displayMetrics.density, fillPaint)
            fillPaint.color = Color.BLACK
            fillPaint.textSize = 13f * resources.configuration.fontScale * resources.displayMetrics.density
            canvas.drawText("${index + 1}", displayPoint.x * width - 4f, displayPoint.y * height + 5f, fillPaint)
        }
        state.detections.forEach { evidence ->
            val box = evidence.box
            val topLeft = map(NPoint(box.left, box.top))
            val bottomRight = map(NPoint(box.right, box.bottom))
            canvas.drawRect(topLeft.x * width, topLeft.y * height, bottomRight.x * width, bottomRight.y * height, linePaint)
        }
    }

    private fun drawPolygon(canvas: Canvas, polygon: Polygon) {
        val path = Path()
        polygon.points.forEachIndexed { index, point ->
            val displayPoint = map(point)
            val x = displayPoint.x * width
            val y = displayPoint.y * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, linePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP || width == 0 || height == 0) return true
        val displayPoint = NPoint(event.x / width, event.y / height)
        val analysisPoint = coordinateMapper?.toAnalysis(displayPoint) ?: displayPoint
        if (analysisPoint.x in 0f..1f && analysisPoint.y in 0f..1f) {
            onNormalizedTap?.invoke(analysisPoint)
        }
        performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun map(point: NPoint): NPoint = coordinateMapper?.toPreview(point) ?: point
}
