package com.infinitecanvas.board.domain.spatial

import com.google.common.truth.Truth.assertThat
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.WorldBounds
import org.junit.Test

class QuadtreeTest {

    private fun stroke(id: Long, vararg pts: Pair<Double, Double>): Stroke =
        Stroke.create(
            id = id,
            points = pts.map { StrokePoint(x = it.first, y = it.second, pressure = 0.5f) },
            brush = Brush()
        )

    private fun queryAll(tree: Quadtree, area: WorldBounds): List<Stroke> {
        val result = ArrayList<Stroke>()
        tree.query(area, result)
        return result
    }

    @Test
    fun `empty tree query returns nothing`() {
        val tree = Quadtree.build(emptyList())

        val result = queryAll(tree, WorldBounds(-100.0, -100.0, 100.0, 100.0))

        assertThat(result).isEmpty()
    }

    @Test
    fun `insert then query with intersecting area returns stroke`() {
        val tree = Quadtree.build(listOf(stroke(1L, 0.0 to 0.0, 10.0 to 10.0)))

        val result = queryAll(tree, WorldBounds(0.0, 0.0, 5.0, 5.0))

        assertThat(result.map { it.id }).containsExactly(1L)
    }

    @Test
    fun `query with disjoint area returns nothing`() {
        val tree = Quadtree.build(listOf(stroke(1L, 0.0 to 0.0)))

        val result = queryAll(tree, WorldBounds(1000.0, 1000.0, 2000.0, 2000.0))

        assertThat(result).isEmpty()
    }

    @Test
    fun `remove deletes stroke from query results`() {
        val tree = Quadtree.build(listOf(stroke(1L, 0.0 to 0.0), stroke(2L, 50.0 to 50.0)))

        tree.remove(1L)

        val result = queryAll(tree, WorldBounds(-100.0, -100.0, 100.0, 100.0))
        assertThat(result.map { it.id }).containsExactly(2L)
    }

    @Test
    fun `remove missing id is no-op`() {
        val tree = Quadtree.build(listOf(stroke(1L, 0.0 to 0.0)))

        tree.remove(99L)

        val result = queryAll(tree, WorldBounds(-100.0, -100.0, 100.0, 100.0))
        assertThat(result.map { it.id }).containsExactly(1L)
    }

    @Test
    fun `many strokes survive subdivision and are all found`() {
        val strokes = (0 until 30).map { stroke(it.toLong(), it * 100.0 to 0.0) }
        val tree = Quadtree.build(strokes)

        val result = queryAll(tree, WorldBounds(-1000.0, -1000.0, 10000.0, 1000.0))

        assertThat(result).hasSize(30)
        assertThat(result.map { it.id }.toSet()).isEqualTo(strokes.map { it.id }.toSet())
    }

    @Test
    fun `findHitStrokeIds hits single-point stroke`() {
        val tree = Quadtree.build(listOf(stroke(1L, 0.0 to 0.0)))

        val hits = tree.findHitStrokeIds(
            worldX = 0.0,
            worldY = 0.0,
            eraserRadius = 1.0,
            candidatesBuffer = ArrayList(),
            hitIdsBuffer = HashSet()
        )

        assertThat(hits).containsExactly(1L)
    }

    @Test
    fun `findHitStrokeIds hits stroke segment`() {
        val tree = Quadtree.build(listOf(stroke(1L, 0.0 to 0.0, 10.0 to 0.0)))

        val hits = tree.findHitStrokeIds(
            worldX = 5.0,
            worldY = 0.0,
            eraserRadius = 1.0,
            candidatesBuffer = ArrayList(),
            hitIdsBuffer = HashSet()
        )

        assertThat(hits).containsExactly(1L)
    }

    @Test
    fun `findHitStrokeIds misses distant stroke`() {
        val tree = Quadtree.build(listOf(stroke(1L, 0.0 to 0.0, 10.0 to 0.0)))

        val hits = tree.findHitStrokeIds(
            worldX = 100.0,
            worldY = 100.0,
            eraserRadius = 1.0,
            candidatesBuffer = ArrayList(),
            hitIdsBuffer = HashSet()
        )

        assertThat(hits).isEmpty()
    }

    @Test
    fun `findHitStrokeIds ignores stroke with no points`() {
        val empty = Stroke(
            id = 1L,
            points = emptyList(),
            brush = Brush(),
            bounds = WorldBounds(-1.0, -1.0, 1.0, 1.0)
        )
        val tree = Quadtree.build(listOf(empty))

        val hits = tree.findHitStrokeIds(
            worldX = 0.0,
            worldY = 0.0,
            eraserRadius = 1.0,
            candidatesBuffer = ArrayList(),
            hitIdsBuffer = HashSet()
        )

        assertThat(hits).isEmpty()
    }
}
