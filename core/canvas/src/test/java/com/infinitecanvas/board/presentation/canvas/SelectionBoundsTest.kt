package com.infinitecanvas.board.presentation.canvas

import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.testing.StrokeFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тесты чистой функции computeSelectionWorldBounds (SelectionBounds.kt).
 *
 * Контракт:
 *   - null при пустом selectedIds или отсутствии совпадений
 *   - иначе — merged bounds выбранных штрихов с padding = 8.0 по всем сторонам
 *   - не мутирует исходные strokes
 */
class SelectionBoundsTest {

    @Test
    fun `empty selection returns null`() {
        assertNull(computeSelectionWorldBounds(emptyList(), emptySet()))
    }

    @Test
    fun `no matching strokes returns null`() {
        val s = StrokeFixtures.stroke(1L, WorldBounds(0.0, 0.0, 100.0, 100.0))
        assertNull(computeSelectionWorldBounds(listOf(s), setOf(999L)))
    }

    @Test
    fun `single stroke gets padding on all sides`() {
        val s = StrokeFixtures.stroke(1L, WorldBounds(10.0, 20.0, 110.0, 120.0))
        val result = computeSelectionWorldBounds(listOf(s), setOf(1L))!!

        assertEquals(2.0,   result.minX, EPS)   // 10  - 8
        assertEquals(12.0,  result.minY, EPS)   // 20  - 8
        assertEquals(118.0, result.maxX, EPS)   // 110 + 8
        assertEquals(128.0, result.maxY, EPS)   // 120 + 8
    }

    @Test
    fun `multiple selected strokes merge and get single padding`() {
        val s1 = StrokeFixtures.stroke(1L, WorldBounds(0.0,    0.0,    50.0,   50.0))
        val s2 = StrokeFixtures.stroke(2L, WorldBounds(100.0,  100.0,  200.0,  200.0))
        val s3 = StrokeFixtures.stroke(3L, WorldBounds(1000.0, 1000.0, 2000.0, 2000.0))

        val result = computeSelectionWorldBounds(
            strokes = listOf(s1, s2, s3),
            selectedIds = setOf(1L, 2L)   // s3 НЕ выбран
        )!!

        assertEquals(-8.0,  result.minX, EPS)   // min(0,100) - 8
        assertEquals(-8.0,  result.minY, EPS)
        assertEquals(208.0, result.maxX, EPS)   // max(50,200) + 8
        assertEquals(208.0, result.maxY, EPS)
    }

    @Test
    fun `unselected strokes do not affect result`() {
        val s1 = StrokeFixtures.stroke(1L, WorldBounds(0.0,    0.0,    50.0,   50.0))
        val s2 = StrokeFixtures.stroke(2L, WorldBounds(1000.0, 1000.0, 2000.0, 2000.0))

        val result = computeSelectionWorldBounds(listOf(s1, s2), setOf(1L))!!

        assertEquals(-8.0, result.minX, EPS)
        assertEquals(-8.0, result.minY, EPS)
        assertEquals(58.0, result.maxX, EPS)
        assertEquals(58.0, result.maxY, EPS)
    }

    @Test
    fun `function is pure and does not mutate input bounds`() {
        val original = WorldBounds(10.0, 20.0, 110.0, 120.0)
        val s = StrokeFixtures.stroke(1L, original)

        val r1 = computeSelectionWorldBounds(listOf(s), setOf(1L))!!
        val r2 = computeSelectionWorldBounds(listOf(s), setOf(1L))!!

        assertEquals(r1, r2)
        // исходные bounds не изменились (padding не накапливается)
        assertEquals(original, s.bounds)
    }

    private companion object {
        const val EPS = 1e-6
    }
}