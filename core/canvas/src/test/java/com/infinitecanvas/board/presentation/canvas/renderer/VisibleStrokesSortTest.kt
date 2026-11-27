package com.infinitecanvas.board.presentation.canvas.renderer

import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.testing.StrokeFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты сортировки видимого набора штрихов (CanvasRenderState.sortVisibleStrokesIfChanged).
 *
 * Покрываемые контракты:
 *   - буфер после вызова отсортирован по id независимо от порядка появления
 *     (порядок обхода quadtree непредсказуем, порядок наложения — нет)
 *   - повторный вызов с тем же составом НЕ пересортировывает и не аллоцирует
 *     (это и есть причина, почему sortBy убран из draw-фазы)
 *   - смена состава (добавление/удаление/подмена id) вызывает пересортировку
 *   - пустой буфер корректно обрабатывается
 *
 * Robolectric нужен из-за Path/PathEffect в конструкторе CanvasRenderState.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VisibleStrokesSortTest {

    private fun bounds() = WorldBounds(0.0, 0.0, 1.0, 1.0)

    private fun stroke(id: Long): Stroke = StrokeFixtures.stroke(id, bounds())

    private fun state() = CanvasRenderState()

    private fun ids(buffer: List<Stroke>): List<Long> = buffer.map { it.id }

    @Test
    fun `sorts buffer by id`() {
        val state = state()
        state.visibleStrokesBuffer.addAll(listOf(stroke(30), stroke(10), stroke(20)))

        state.sortVisibleStrokesIfChanged()

        assertEquals(listOf(10L, 20L, 30L), ids(state.visibleStrokesBuffer))
    }

    @Test
    fun `already sorted buffer stays sorted`() {
        val state = state()
        state.visibleStrokesBuffer.addAll(listOf(stroke(1), stroke(2), stroke(3)))

        state.sortVisibleStrokesIfChanged()

        assertEquals(listOf(1L, 2L, 3L), ids(state.visibleStrokesBuffer))
    }

    @Test
    fun `repeated calls with same content do not change order`() {
        val state = state()
        state.visibleStrokesBuffer.addAll(listOf(stroke(30), stroke(10), stroke(20)))

        state.sortVisibleStrokesIfChanged()
        val afterFirst = ids(state.visibleStrokesBuffer)
        state.sortVisibleStrokesIfChanged()
        state.sortVisibleStrokesIfChanged()

        assertEquals(afterFirst, ids(state.visibleStrokesBuffer))
        assertEquals(listOf(10L, 20L, 30L), ids(state.visibleStrokesBuffer))
    }

    @Test
    fun `resorts when a stroke is added`() {
        val state = state()
        state.visibleStrokesBuffer.addAll(listOf(stroke(10), stroke(20)))
        state.sortVisibleStrokesIfChanged()

        state.visibleStrokesBuffer.add(stroke(15))
        state.sortVisibleStrokesIfChanged()

        assertEquals(listOf(10L, 15L, 20L), ids(state.visibleStrokesBuffer))
    }

    @Test
    fun `resorts when a stroke is removed`() {
        val state = state()
        state.visibleStrokesBuffer.addAll(listOf(stroke(10), stroke(20), stroke(30)))
        state.sortVisibleStrokesIfChanged()

        // Симулируем следующий query: новый состав без 20 и в порядке обхода дерева.
        state.visibleStrokesBuffer.clear()
        state.visibleStrokesBuffer.addAll(listOf(stroke(30), stroke(10)))
        state.sortVisibleStrokesIfChanged()

        assertEquals(listOf(10L, 30L), ids(state.visibleStrokesBuffer))
    }

    @Test
    fun `resorts when order of same ids changes`() {
        val state = state()
        state.visibleStrokesBuffer.addAll(listOf(stroke(10), stroke(20)))
        state.sortVisibleStrokesIfChanged()

        // Тот же набор, но дерево вернуло его в обратном порядке — отпечаток обязан
        // отличаться, иначе на экране закрепился бы неверный порядок наложения.
        state.visibleStrokesBuffer.clear()
        state.visibleStrokesBuffer.addAll(listOf(stroke(20), stroke(10)))
        state.sortVisibleStrokesIfChanged()

        assertEquals(listOf(10L, 20L), ids(state.visibleStrokesBuffer))
    }

    @Test
    fun `empty buffer is handled`() {
        val state = state()

        state.sortVisibleStrokesIfChanged()

        assertTrue(state.visibleStrokesBuffer.isEmpty())
    }

    @Test
    fun `single stroke buffer is unchanged`() {
        val state = state()
        state.visibleStrokesBuffer.add(stroke(42))

        state.sortVisibleStrokesIfChanged()

        assertEquals(listOf(42L), ids(state.visibleStrokesBuffer))
    }
}
