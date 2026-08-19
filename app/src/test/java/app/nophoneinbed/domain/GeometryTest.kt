package app.nophoneinbed.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeometryTest {
    @Test
    fun rectangleIntersectionUsesClippedArea() {
        val polygon = Polygon.rectangle(0.2f, 0.2f, 0.8f, 0.8f)
        val rect = NRect(0.7f, 0.4f, 0.9f, 0.6f)

        assertThat(polygon.intersectionArea(rect)).isWithin(0.0001f).of(0.02f)
    }

    @Test
    fun convexHullDropsInteriorPoints() {
        val hull = Polygon.convexHull(
            listOf(
                NPoint(0.1f, 0.1f),
                NPoint(0.9f, 0.1f),
                NPoint(0.9f, 0.9f),
                NPoint(0.1f, 0.9f),
                NPoint(0.5f, 0.5f),
            ),
        )

        assertThat(hull.points).hasSize(4)
        assertThat(hull.contains(NPoint(0.5f, 0.5f))).isTrue()
    }

    @Test
    fun polygonDistinguishesInsideEdgeAndOutside() {
        val polygon = Polygon.rectangle(0.2f, 0.2f, 0.8f, 0.8f)

        assertThat(polygon.contains(NPoint(0.5f, 0.5f))).isTrue()
        assertThat(polygon.contains(NPoint(0.2f, 0.5f))).isTrue()
        assertThat(polygon.contains(NPoint(0.1f, 0.5f))).isFalse()
    }
}
