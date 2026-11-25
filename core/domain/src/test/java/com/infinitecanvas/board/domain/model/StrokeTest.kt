package com.infinitecanvas.board.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StrokeTest {

    @Test
    fun `create computes bounds padded by ballpoint width`() {
        val stroke = Stroke.create(
            id = 1L,
            points = listOf(
                StrokePoint(x = 0.0, y = 0.0, pressure = 0.5f),
                StrokePoint(x = 10.0, y = 10.0, pressure = 0.5f)
            ),
            brush = Brush(type = BrushType.BALLPOINT, baseSize = 4f)
        )

        // BALLPOINT: visualWidth = 4 * 1.05 = 4.2, padding = 4.2*0.65 ≈ 2.73 → clamped to 4.0
        assertThat(stroke.bounds.minX).isWithin(1e-9).of(-4.0)
        assertThat(stroke.bounds.maxX).isWithin(1e-9).of(14.0)
        assertThat(stroke.bounds.minY).isWithin(1e-9).of(-4.0)
        assertThat(stroke.bounds.maxY).isWithin(1e-9).of(14.0)
    }

    @Test
    fun `create uses larger padding for highlighter`() {
        val stroke = Stroke.create(
            id = 1L,
            points = listOf(StrokePoint(x = 0.0, y = 0.0, pressure = 0.5f)),
            brush = Brush(type = BrushType.HIGHLIGHTER, baseSize = 4f)
        )

        // HIGHLIGHTER: visualWidth = 4 * 6 = 24, padding = 24*0.65 = 15.6
        assertThat(stroke.bounds.minX).isWithin(1e-9).of(-15.6)
        assertThat(stroke.bounds.maxX).isWithin(1e-9).of(15.6)
    }

    @Test
    fun `create with empty points yields infinite bounds`() {
        val stroke = Stroke.create(id = 1L, points = emptyList(), brush = Brush())

        assertThat(stroke.bounds.minX).isPositiveInfinity()
        assertThat(stroke.bounds.minY).isPositiveInfinity()
        assertThat(stroke.bounds.maxX).isNegativeInfinity()
        assertThat(stroke.bounds.maxY).isNegativeInfinity()
    }

    @Test
    fun `translated with zero shift returns same instance`() {
        val stroke = Stroke.create(
            id = 1L,
            points = listOf(StrokePoint(x = 0.0, y = 0.0, pressure = 0.5f)),
            brush = Brush()
        )

        assertThat(stroke.translated(0.0, 0.0)).isSameInstanceAs(stroke)
    }

    @Test
    fun `translated shifts points and bounds and keeps id and brush`() {
        val stroke = Stroke.create(
            id = 1L,
            points = listOf(
                StrokePoint(x = 0.0, y = 0.0, pressure = 0.5f),
                StrokePoint(x = 10.0, y = 10.0, pressure = 0.5f)
            ),
            brush = Brush(type = BrushType.BALLPOINT, baseSize = 4f)
        )

        val moved = stroke.translated(5.0, -3.0)

        assertThat(moved).isNotSameInstanceAs(stroke)
        assertThat(moved.id).isEqualTo(1L)
        assertThat(moved.brush).isEqualTo(stroke.brush)
        assertThat(moved.points[0].x).isEqualTo(5.0)
        assertThat(moved.points[0].y).isEqualTo(-3.0)
        assertThat(moved.bounds.minX).isEqualTo(stroke.bounds.minX + 5.0)
        assertThat(moved.bounds.minY).isEqualTo(stroke.bounds.minY - 3.0)
    }
}
