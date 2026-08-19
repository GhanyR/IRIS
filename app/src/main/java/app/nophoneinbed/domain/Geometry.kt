package app.nophoneinbed.domain

import kotlin.math.abs

data class NPoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "Point coordinates must be finite" }
    }
}

data class NRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(listOf(left, top, right, bottom).all(Float::isFinite)) {
            "Rectangle coordinates must be finite"
        }
        require(right > left && bottom > top) { "Rectangle must have positive area" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
    val center: NPoint get() = NPoint((left + right) / 2f, (top + bottom) / 2f)

    fun intersection(other: NRect): NRect? {
        val overlapLeft = maxOf(left, other.left)
        val overlapTop = maxOf(top, other.top)
        val overlapRight = minOf(right, other.right)
        val overlapBottom = minOf(bottom, other.bottom)
        return if (overlapRight > overlapLeft && overlapBottom > overlapTop) {
            NRect(overlapLeft, overlapTop, overlapRight, overlapBottom)
        } else {
            null
        }
    }

    fun iou(other: NRect): Float {
        val intersectionArea = intersection(other)?.area ?: 0f
        return intersectionArea / (area + other.area - intersectionArea)
    }
}

class Polygon(points: List<NPoint>) {
    val points: List<NPoint> = points.toList()

    init {
        require(this.points.size >= 3) { "Polygon needs at least three points" }
    }

    val signedArea: Float
        get() = points.indices.sumOf { index ->
            val current = points[index]
            val next = points[(index + 1) % points.size]
            (current.x * next.y - next.x * current.y).toDouble()
        }.toFloat() / 2f

    val area: Float get() = abs(signedArea)

    fun contains(point: NPoint): Boolean {
        var inside = false
        var previous = points.last()
        for (current in points) {
            if (pointOnSegment(point, previous, current)) return true
            val crossesRay = (current.y > point.y) != (previous.y > point.y)
            if (crossesRay) {
                val xAtY = (previous.x - current.x) * (point.y - current.y) /
                    (previous.y - current.y) + current.x
                if (point.x < xAtY) inside = !inside
            }
            previous = current
        }
        return inside
    }

    fun intersectionArea(rect: NRect): Float {
        var clipped = points
        clipped = clip(clipped, { it.x >= rect.left }) { a, b -> intersectVertical(a, b, rect.left) }
        clipped = clip(clipped, { it.x <= rect.right }) { a, b -> intersectVertical(a, b, rect.right) }
        clipped = clip(clipped, { it.y >= rect.top }) { a, b -> intersectHorizontal(a, b, rect.top) }
        clipped = clip(clipped, { it.y <= rect.bottom }) { a, b -> intersectHorizontal(a, b, rect.bottom) }
        return if (clipped.size >= 3) Polygon(clipped).area else 0f
    }

    fun isConvex(): Boolean {
        var direction = 0
        for (index in points.indices) {
            val cross = cross(
                points[index],
                points[(index + 1) % points.size],
                points[(index + 2) % points.size],
            )
            if (abs(cross) <= EPSILON) continue
            val currentDirection = if (cross > 0f) 1 else -1
            if (direction != 0 && direction != currentDirection) return false
            direction = currentDirection
        }
        return direction != 0
    }

    companion object {
        private const val EPSILON = 1e-6f

        fun rectangle(left: Float, top: Float, right: Float, bottom: Float): Polygon =
            Polygon(
                listOf(
                    NPoint(left, top),
                    NPoint(right, top),
                    NPoint(right, bottom),
                    NPoint(left, bottom),
                ),
            )

        fun convexHull(input: List<NPoint>): Polygon {
            val sorted = input.distinct().sortedWith(compareBy<NPoint> { it.x }.thenBy { it.y })
            require(sorted.size >= 3) { "Convex hull needs three unique points" }

            val lower = mutableListOf<NPoint>()
            for (point in sorted) {
                while (lower.size >= 2 && cross(lower[lower.lastIndex - 1], lower.last(), point) <= 0f) {
                    lower.removeAt(lower.lastIndex)
                }
                lower += point
            }

            val upper = mutableListOf<NPoint>()
            for (point in sorted.asReversed()) {
                while (upper.size >= 2 && cross(upper[upper.lastIndex - 1], upper.last(), point) <= 0f) {
                    upper.removeAt(upper.lastIndex)
                }
                upper += point
            }

            return Polygon(lower.dropLast(1) + upper.dropLast(1))
        }

        fun segmentsCross(a: NPoint, b: NPoint, c: NPoint, d: NPoint): Boolean {
            val abC = cross(a, b, c)
            val abD = cross(a, b, d)
            val cdA = cross(c, d, a)
            val cdB = cross(c, d, b)
            return abC * abD < -EPSILON && cdA * cdB < -EPSILON
        }

        private fun cross(origin: NPoint, a: NPoint, b: NPoint): Float =
            (a.x - origin.x) * (b.y - origin.y) - (a.y - origin.y) * (b.x - origin.x)

        private fun pointOnSegment(point: NPoint, a: NPoint, b: NPoint): Boolean {
            if (abs(cross(a, b, point)) > EPSILON) return false
            return point.x in minOf(a.x, b.x) - EPSILON..maxOf(a.x, b.x) + EPSILON &&
                point.y in minOf(a.y, b.y) - EPSILON..maxOf(a.y, b.y) + EPSILON
        }

        private fun clip(
            input: List<NPoint>,
            isInside: (NPoint) -> Boolean,
            intersection: (NPoint, NPoint) -> NPoint,
        ): List<NPoint> {
            if (input.isEmpty()) return emptyList()
            val output = mutableListOf<NPoint>()
            var previous = input.last()
            var previousInside = isInside(previous)
            for (current in input) {
                val currentInside = isInside(current)
                if (currentInside) {
                    if (!previousInside) output += intersection(previous, current)
                    output += current
                } else if (previousInside) {
                    output += intersection(previous, current)
                }
                previous = current
                previousInside = currentInside
            }
            return output
        }

        private fun intersectVertical(a: NPoint, b: NPoint, x: Float): NPoint {
            val fraction = (x - a.x) / (b.x - a.x)
            return NPoint(x, a.y + fraction * (b.y - a.y))
        }

        private fun intersectHorizontal(a: NPoint, b: NPoint, y: Float): NPoint {
            val fraction = (y - a.y) / (b.y - a.y)
            return NPoint(a.x + fraction * (b.x - a.x), y)
        }
    }
}
