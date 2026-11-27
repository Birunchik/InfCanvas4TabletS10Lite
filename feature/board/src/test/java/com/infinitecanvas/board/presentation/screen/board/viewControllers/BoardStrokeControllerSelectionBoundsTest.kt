package com.infinitecanvas.board.presentation.screen.board.viewControllers

import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.testing.FakeIdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тесты инкрементального индекса id → bounds в BoardStrokeController.
 *
 * Причина существования этих тестов: recomputeSelectionBounds переведён с полного
 * прохода по _strokes на кэш Map<id, bounds>. Кэш обязан инвалидироваться при КАЖДОЙ
 * мутации списка, иначе рамка выделения посчитается по старой геометрии — визуально
 * незаметный, но реальный баг. Каждый тест ломает ровно один мутатор и проверяет,
 * что bounds после него пересчитаны заново.
 */
class BoardStrokeControllerSelectionBoundsTest {

    private val scope = TestScope(StandardTestDispatcher())
    private val idGenerator = FakeIdGenerator()

    private fun controller(): BoardStrokeController =
        BoardStrokeController(
            scope = scope,
            idGenerator = idGenerator,
            onContentChanged = {},
            onSelectionActivated = {}
        )

    private fun stroke(id: Long, minX: Double, minY: Double, size: Double = 10.0): Stroke =
        Stroke.create(
            id = id,
            points = listOf(
                StrokePoint(minX + size / 2, minY + size / 2, 0.5f),
                StrokePoint(minX + size, minY + size, 0.5f)
            ),
            brush = Brush()
        )

    // padding в computeSelectionWorldBounds = 8.0
    private val pad = 8.0

    @Test
    fun `selection bounds are null for empty selection`() {
        val c = controller()
        c.setInitialStrokes(listOf(stroke(1L, 0.0, 0.0)))

        assertNull(c.selectionBounds.value)
    }

    @Test
    fun `selectStrokes computes bounds with padding`() {
        val c = controller()
        val s = stroke(1L, 100.0, 200.0, size = 50.0)
        c.setInitialStrokes(listOf(s))

        c.selectStrokes(setOf(1L))

        val b = c.selectionBounds.value!!
        assertEquals(s.bounds.minX - pad, b.minX, EPS)
        assertEquals(s.bounds.maxX + pad, b.maxX, EPS)
    }

    @Test
    fun `index is rebuilt after moveSelectedStrokes`() {
        val c = controller()
        val s = stroke(1L, 0.0, 0.0, size = 10.0)
        c.setInitialStrokes(listOf(s))
        c.selectStrokes(setOf(1L))
        val before = c.selectionBounds.value!!

        c.moveSelectedStrokes(500.0, 0.0)
        // После драга selectionBounds сдвигается арифметикой; затем повторный выбор
        // обязан пересобрать индекс и увидеть НОВЫЕ bounds, а не старые из кэша.
        c.selectStrokes(emptySet())
        c.selectStrokes(setOf(1L))

        val after = c.selectionBounds.value!!
        assertEquals(before.minX + 500.0, after.minX, EPS)
        assertEquals(before.maxX + 500.0, after.maxX, EPS)
    }

    @Test
    fun `index is rebuilt after erase`() {
        val c = controller()
        val s1 = stroke(1L, 0.0, 0.0)
        val s2 = stroke(2L, 1000.0, 1000.0)
        c.setInitialStrokes(listOf(s1, s2))
        c.selectStrokes(setOf(1L, 2L))
        val both = c.selectionBounds.value!!

        c.eraseStrokesByIds(setOf(2L))
        c.selectStrokes(setOf(1L))

        val only1 = c.selectionBounds.value!!
        // Рамка сузилась до одного штриха: если бы индекс остался старым, maxX
        // всё ещё включал бы s2 (1000+).
        assertEquals(true, only1.maxX < both.maxX)
        assertEquals(s1.bounds.maxX + pad, only1.maxX, EPS)
    }

    @Test
    fun `index is rebuilt after undo`() {
        val c = controller()
        val s1 = stroke(1L, 0.0, 0.0)
        c.setInitialStrokes(listOf(s1))

        // Коммитим второй штрих далеко в стороне, затем откатываем.
        val s2 = stroke(2L, 2000.0, 2000.0)
        c.onStrokeCommitted(s2)
        c.selectStrokes(setOf(2L))
        assertNotNull(c.selectionBounds.value)

        c.undo()
        // После undo s2 больше нет в списке — рамка по его id должна стать null,
        // а не взяться из устаревшего индекса.
        c.selectStrokes(setOf(2L))
        assertNull(c.selectionBounds.value)
    }

    @Test
    fun `index is rebuilt after paste`() {
        val c = controller()
        val s = stroke(1L, 10.0, 10.0, size = 20.0)
        c.setInitialStrokes(listOf(s))
        c.selectStrokes(setOf(1L))
        c.copySelectedStrokes()

        c.pasteClipboardStrokes()

        // После вставки выделение переключается на новые id — bounds обязаны быть непустыми,
        // а значит новый индекс содержит вставленные штрихи.
        c.selectStrokes(c.selectedStrokeIds.value)
        assertNotNull(c.selectionBounds.value)
        assertEquals(true, c.selectionBounds.value!!.minX > s.bounds.minX)
    }

    @Test
    fun `index is rebuilt after setInitialStrokes replaces content`() {
        val c = controller()
        c.setInitialStrokes(listOf(stroke(1L, 0.0, 0.0)))
        c.selectStrokes(setOf(1L))
        assertNotNull(c.selectionBounds.value)

        // Загрузка другой доски: id из старого документа невалидны.
        c.setInitialStrokes(listOf(stroke(7L, 300.0, 300.0)))
        c.selectStrokes(setOf(1L))
        assertNull(c.selectionBounds.value)

        c.selectStrokes(setOf(7L))
        assertNotNull(c.selectionBounds.value)
    }

    private companion object {
        const val EPS = 1e-3
    }
}
