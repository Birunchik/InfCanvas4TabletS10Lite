package com.infinitecanvas.board.domain.spatial

import com.google.common.truth.Truth.assertThat
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import org.junit.Test

class LassoSelectorTest {

    private fun stroke(id: Long, vararg pts: Pair<Double, Double>): Stroke =
        Stroke.create(
            id = id,
            points = pts.map { StrokePoint(x = it.first, y = it.second, pressure = 0.5f) },
            brush = Brush()
        )

    private fun pt(x: Double, y: Double) = StrokePoint(x, y, 0.5f)

    private fun triangle(): List<StrokePoint> = listOf(pt(0.0, 0.0), pt(100.0, 0.0), pt(0.0, 100.0))

    @Test
    fun `polygon with fewer than 3 points selects nothing`() {
        val tree = Quadtree.build(listOf(stroke(1L, 20.0 to 20.0)))
        val polygon = listOf(pt(0.0, 0.0), pt(100.0, 0.0))

        val ids = LassoSelector.findSelectedStrokeIds(polygon, tree)

        assertThat(ids).isEmpty()
    }

    @Test
    fun `selects stroke fully inside polygon`() {
        val tree = Quadtree.build(listOf(stroke(1L, 20.0 to 20.0)))

        val ids = LassoSelector.findSelectedStrokeIds(triangle(), tree)

        assertThat(ids).containsExactly(1L)
    }

    @Test
    fun `does not select stroke inside AABB but outside polygon`() {
        // (80, 80) лежит в AABB полигона, но вне треугольника.
        val tree = Quadtree.build(listOf(stroke(1L, 80.0 to 80.0)))

        val ids = LassoSelector.findSelectedStrokeIds(triangle(), tree)

        assertThat(ids).isEmpty()
    }

    @Test
    fun `does not select stroke far outside polygon`() {
        val tree = Quadtree.build(listOf(stroke(1L, 500.0 to 500.0)))

        val ids = LassoSelector.findSelectedStrokeIds(triangle(), tree)

        assertThat(ids).isEmpty()
    }

    @Test
    fun `empty quadtree selects nothing`() {
        val ids = LassoSelector.findSelectedStrokeIds(triangle(), Quadtree.build(emptyList()))
        assertThat(ids).isEmpty()
    }

    @Test
    fun `selects stroke with majority of points inside`() {
        val tree = Quadtree.build(listOf(stroke(1L, 10.0 to 10.0, 20.0 to 20.0, 30.0 to 30.0)))

        val ids = LassoSelector.findSelectedStrokeIds(triangle(), tree)

        assertThat(ids).containsExactly(1L)
    }

    @Test
    fun `does not select stroke with majority of points outside`() {
        // Одна точка внутри AABB, две — вне треугольника.
        val tree = Quadtree.build(listOf(stroke(1L, 20.0 to 20.0, 80.0 to 80.0, 90.0 to 90.0)))

        val ids = LassoSelector.findSelectedStrokeIds(triangle(), tree)

        assertThat(ids).isEmpty()
    }
}
