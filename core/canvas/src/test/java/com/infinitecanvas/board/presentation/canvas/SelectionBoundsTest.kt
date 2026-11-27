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

    // ---- map-вариант: обязан давать побитово тот же результат, что list-вариант ----

    private fun mapOf(vararg strokes: Pair<Long, WorldBounds>): Map<Long, WorldBounds> =
        strokes.toMap()

    @Test
    fun `map variant is null for empty selection`() {
        assertNull(computeSelectionWorldBounds(emptySet(), mapOf(1L to WorldBounds(0.0, 0.0, 1.0, 1.0))))
    }

    @Test
    fun `map variant is null when no id is present`() {
        assertNull(computeSelectionWorldBounds(setOf(999L), mapOf(1L to WorldBounds(0.0, 0.0, 1.0, 1.0))))
    }

    @Test
    fun `map variant matches list variant for single stroke`() {
        val b = WorldBounds(10.0, 20.0, 110.0, 120.0)
        val s = StrokeFixtures.stroke(1L, b)

        val fromList = computeSelectionWorldBounds(listOf(s), setOf(1L))
        val fromMap = computeSelectionWorldBounds(setOf(1L), mapOf(1L to b))

        assertEquals(fromList, fromMap)
    }

    @Test
    fun `map variant matches list variant for multiple strokes`() {
        val b1 = WorldBounds(0.0, 0.0, 50.0, 50.0)
        val b2 = WorldBounds(-30.0, 10.0, 200.0, 80.0)
        val b3 = WorldBounds(500.0, 500.0, 600.0, 600.0)
        val strokes = listOf(
            StrokeFixtures.stroke(1L, b1),
            StrokeFixtures.stroke(2L, b2),
            StrokeFixtures.stroke(3L, b3)
        )
        val selection = setOf(1L, 2L)

        val fromList = computeSelectionWorldBounds(strokes, selection)
        val fromMap = computeSelectionWorldBounds(selection, mapOf(1L to b1, 2L to b2, 3L to b3))

        assertEquals(fromList, fromMap)
        // и результат не зависит от порядка id в наборе
        assertEquals(fromList, computeSelectionWorldBounds(setOf(2L, 1L), mapOf(1L to b1, 2L to b2, 3L to b3)))
    }

    @Test
    fun `map variant ignores ids missing from the index`() {
        val b = WorldBounds(0.0, 0.0, 10.0, 10.0)
        val result = computeSelectionWorldBounds(setOf(1L, 404L), mapOf(1L to b))!!
        assertEquals(-8.0, result.minX, EPS)
        assertEquals(18.0, result.maxX, EPS)
    }

    @Test
    fun `map variant does not depend on how many strokes exist outside selection`() {
        // Смысл правки: стоимость зависит от размера выделения, а не доски.
        // Здесь проверяем эквивалентность результата при большом индексе.
        val bigIndex = HashMap<Long, WorldBounds>()
        for (i in 1L..10_000L) {
            bigIndex[i] = WorldBounds(i.toDouble(), i.toDouble(), i + 1.0, i + 1.0)
        }
        val result = computeSelectionWorldBounds(setOf(5_000L), bigIndex)!!
        assertEquals(5_000.0 - 8.0, result.minX, EPS)
        assertEquals(5_001.0 + 8.0, result.maxX, EPS)
    }

    private companion object {
        const val EPS = 1e-6
    }
}