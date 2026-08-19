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
    private var draggedCornerIndex: Int? = null
    var coordinateMapper: CoordinateMapper? = null
    var onNormalizedTap: ((NPoint) -> Unit)? = null
    var onNormalizedDrag: ((Int, NPoint) -> Unit)? = null
    var calibrationEditable: Boolean = false

    fun render(newState: OverlayRenderState) {
        state = newState
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val trackerColor = when (state.trackerState) {
            TrackerState.ALARM -> Color.RED
            TrackerState.WATCH -> Color.YELLOW
            TrackerState.FAULT -> Color.MAGENTA
            TrackerState.CLEAR -> Color.GREEN
        }
        linePaint.color = trackerColor
        state.projectedVolume?.let { drawPolygon(canvas, it) }
        if (state.calibrationCorners.size >= 2) {
            linePaint.color = Color.CYAN
            drawCalibrationGuide(canvas, state.calibrationCorners)
        }
        state.calibrationCorners.forEachIndexed { index, point ->
            val displayPoint = map(point)
            fillPaint.color = Color.CYAN
            canvas.drawCircle(displayPoint.x * width, displayPoint.y * height, 9f * resources.displayMetrics.density, fillPaint)
            fillPaint.color = Color.BLACK
            fillPaint.textSize = 13f * resources.configuration.fontScale * resources.displayMetrics.density
            canvas.drawText("${index + 1}", displayPoint.x * width - 4f, displayPoint.y * height + 5f, fillPaint)
        }
        linePaint.color = trackerColor
        state.detections.forEach { evidence ->
            val box = evidence.box
            val topLeft = map(NPoint(box.left, box.top))
            val bottomRight = map(NPoint(box.right, box.bottom))
            canvas.drawRect(topLeft.x * width, topLeft.y * height, bottomRight.x * width, bottomRight.y * height, linePaint)
        }
    }

    private fun drawCalibrationGuide(canvas: Canvas, corners: List<NPoint>) {
        val path = Path()
        corners.forEachIndexed { index, point ->
            val displayPoint = map(point)
            val x = displayPoint.x * width
            val y = displayPoint.y * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (corners.size == 4) path.close()
        canvas.drawPath(path, linePaint)
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
        if (width == 0 || height == 0) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                draggedCornerIndex = if (calibrationEditable && state.calibrationCorners.size == 4) {
                    nearestCorner(event.x, event.y)
                } else {
                    null
                }
            }
            MotionEvent.ACTION_MOVE -> {
                draggedCornerIndex?.let { index -> normalizedPoint(event)?.let { onNormalizedDrag?.invoke(index, it) } }
            }
            MotionEvent.ACTION_UP -> {
                val index = draggedCornerIndex
                if (index != null) {
                    normalizedPoint(event)?.let { onNormalizedDrag?.invoke(index, it) }
                } else {
                    normalizedPoint(event)?.let { onNormalizedTap?.invoke(it) }
                }
                draggedCornerIndex = null
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> draggedCornerIndex = null
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun normalizedPoint(event: MotionEvent): NPoint? {
        val displayPoint = NPoint(event.x / width, event.y / height)
        val analysisPoint = coordinateMapper?.toAnalysis(displayPoint) ?: displayPoint
        return analysisPoint.takeIf { it.x in 0f..1f && it.y in 0f..1f }
    }

    private fun nearestCorner(x: Float, y: Float): Int? {
        val radius = 32f * resources.displayMetrics.density
        val radiusSquared = radius * radius
        return state.calibrationCorners
            .mapIndexed { index, point ->
                val displayPoint = map(point)
                val dx = displayPoint.x * width - x
                val dy = displayPoint.y * height - y
                index to (dx * dx + dy * dy)
            }
            .filter { (_, distance) -> distance <= radiusSquared }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    private fun map(point: NPoint): NPoint = coordinateMapper?.toPreview(point) ?: point
}
