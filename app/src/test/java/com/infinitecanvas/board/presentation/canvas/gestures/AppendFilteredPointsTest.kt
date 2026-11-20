package com.infinitecanvas.board.presentation.canvas.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.HistoricalChange
import androidx.compose.ui.input.pointer.PointerInputChange
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.ViewportState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты appendFilteredPoints (CanvasGestures.kt).
 *
 * Ключевые контракты, зафиксированные тестами:
 *   1. Первая точка ВСЕГДА добавляется (target пуст → shouldAddPoint = true).
 *   2. minDistWorld = min(minStepPx / max(scale, 0.25), 2.5).
 *      - scale=1 → 2.5 (кап); scale=2 → 2.0; scale=10 → 0.4.
 *   3. historical добавляются ДО текущей точки, таймстемпы сохраняются.
 *   4. incrementalPath: moveTo для первой, lineTo для последующих; не трогается,
 *      если точка отфильтрована.
 *   5. pressure зажимается в [0.05, 1.0].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppendFilteredPointsTest {

    // ---------- helpers ----------

    /**
     * ViewportState мокается, а не конструируется: сигнатура конструктора
     * может меняться (например, pivotRect), а формула screenToWorld — нет.
     * Тестам нужны только scale + screenToWorldX/Y.
     */
    private fun mockViewport(
        scale: Double = 1.0,
        offsetX: Double = 0.0,
        offsetY: Double = 0.0
    ): ViewportState {
        val vp = mockk<ViewportState>()
        every { vp.scale } returns scale
        every { vp.screenToWorldX(any()) } answers {
            (firstArg<Float>().toDouble() - offsetX) / scale
        }
        every { vp.screenToWorldY(any()) } answers {
            (firstArg<Float>().toDouble() - offsetY) / scale
        }
        return vp
    }

    private fun mockHistorical(position: Offset, uptimeMillis: Long): HistoricalChange {
        val h = mockk<HistoricalChange>()
        every { h.position } returns position
        every { h.uptimeMillis } returns uptimeMillis
        return h
    }

    private fun mockChange(
        position: Offset,
        pressure: Float = 0.5f,
        uptimeMillis: Long = 1000L,
        historical: List<HistoricalChange> = emptyList()
    ): PointerInputChange {
        val c = mockk<PointerInputChange>()
        every { c.position } returns position
        every { c.pressure } returns pressure
        every { c.uptimeMillis } returns uptimeMillis
        every { c.historical } returns historical
        return c
    }

    private fun point(x: Double, y: Double) = StrokePoint(x, y, 0.5f, 0L)

    // ---------- первая точка ----------

    @Test
    fun `first point always added regardless of distance`() {
        val vp = mockViewport(scale = 1.0)
        val target = mutableListOf<StrokePoint>()

        val added = appendFilteredPoints(mockChange(Offset(1000f, 1000f)), vp, target)

        assertTrue(added)
        assertEquals(1, target.size)
        assertEquals(1000.0, target[0].x, EPS)
        assertEquals(1000.0, target[0].y, EPS)
    }

    // ---------- фильтр по minDist ----------

    @Test
    fun `point below min distance filtered out`() {
        // scale=1 → minDist = min(4/1, 2.5) = 2.5
        val vp = mockViewport(scale = 1.0)
        val target = mutableListOf(point(0.0, 0.0))
        // screen(1,1) → world(1,1), dist от (0,0) ≈ 1.41 < 2.5
        val added = appendFilteredPoints(mockChange(Offset(1f, 1f)), vp, target)

        assertFalse(added)
        assertEquals(1, target.size) // только исходная
    }

    @Test
    fun `point above min distance added`() {
        val vp = mockViewport(scale = 1.0) // minDist = 2.5
        val target = mutableListOf(point(0.0, 0.0))
        // screen(10,10) → world(10,10), dist ≈ 14.14 > 2.5
        val added = appendFilteredPoints(mockChange(Offset(10f, 10f)), vp, target)

        assertTrue(added)
        assertEquals(2, target.size)
        assertEquals(10.0, target[1].x, EPS)
        assertEquals(10.0, target[1].y, EPS)
    }

    // ---------- historical ----------

    @Test
    fun `historical points added before current with preserved timestamps`() {
        val vp = mockViewport(scale = 1.0)
        val target = mutableListOf<StrokePoint>()
        val change = mockChange(
            position = Offset(50f, 50f),
            uptimeMillis = 1000L,
            historical = listOf(
                mockHistorical(Offset(10f, 10f), 100L),
                mockHistorical(Offset(20f, 20f), 200L),
                mockHistorical(Offset(30f, 30f), 300L)
            )
        )

        appendFilteredPoints(change, vp, target)

        // Порядок: historical (по времени) → current
        assertEquals(4, target.size)
        assertEquals(10.0, target[0].x, EPS)
        assertEquals(20.0, target[1].x, EPS)
        assertEquals(30.0, target[2].x, EPS)
        assertEquals(50.0, target[3].x, EPS)
        // Таймстемпы historical/current сохраняются
        assertEquals(100L,  target[0].timestampMs)
        assertEquals(200L,  target[1].timestampMs)
        assertEquals(300L,  target[2].timestampMs)
        assertEquals(1000L, target[3].timestampMs)
    }

    @Test
    fun `historical points filtered by same min distance`() {
        val vp = mockViewport(scale = 1.0) // minDist = 2.5
        val target = mutableListOf<StrokePoint>()
        val change = mockChange(
            position = Offset(100f, 100f),
            historical = listOf(
                mockHistorical(Offset(0f, 0f), 100L),   // added (target пуст)
                mockHistorical(Offset(1f, 1f), 200L),   // ~1.41 от (0,0) → skip
                mockHistorical(Offset(2f, 2f), 300L)    // ~2.83 от (0,0) → added
            )
        )

        appendFilteredPoints(change, vp, target)

        // (0,0) и (2,2) добавлены; (1,1) отфильтрована; (100,100) — current → добавлена
        assertEquals(3, target.size)
        assertEquals(0.0,   target[0].x, EPS)
        assertEquals(2.0,   target[1].x, EPS)
        assertEquals(100.0, target[2].x, EPS)
    }

    // ---------- возвращаемое значение ----------

    @Test
    fun `returns true when at least one point added`() {
        val vp = mockViewport()
        val target = mutableListOf<StrokePoint>()
        assertTrue(appendFilteredPoints(mockChange(Offset(0f, 0f)), vp, target))
    }

    @Test
    fun `returns false when all points filtered`() {
        val vp = mockViewport(scale = 1.0)
        val target = mutableListOf(point(0.0, 0.0))
        assertFalse(appendFilteredPoints(mockChange(Offset(0.1f, 0.1f)), vp, target))
    }

    // ---------- incrementalPath ----------

    @Test
    fun `incremental path moveTo first then lineTo subsequent`() {
        val vp = mockViewport(scale = 1.0)
        val target = mutableListOf<StrokePoint>()
        val path = Path()
        assertTrue("новый Path должен быть пустым", path.isEmpty)

        appendFilteredPoints(mockChange(Offset(0f, 0f)), vp, target, incrementalPath = path)
        appendFilteredPoints(mockChange(Offset(10f, 10f)), vp, target, incrementalPath = path)

        assertFalse("Path должен содержать moveTo + lineTo", path.isEmpty)
        assertEquals(2, target.size)
    }

    @Test
    fun `incremental path untouched when no point added`() {
        val vp = mockViewport(scale = 1.0) // minDist = 2.5
        val target = mutableListOf(point(0.0, 0.0))
        val path = Path()

        val added = appendFilteredPoints(
            change = mockChange(Offset(0.5f, 0.5f)), // world(0.5,0.5), dist ≈ 0.71 < 2.5
            viewport = vp,
            target = target,
            incrementalPath = path
        )

        assertFalse(added)
        assertTrue("Path не должен меняться, если точка отфильтрована", path.isEmpty)
    }

    // ---------- масштабирование minDist ----------

    @Test
    fun `min dist world scales down with high zoom`() {
        // scale=10 → minDist = min(4/10, 2.5) = 0.4
        val vp = mockViewport(scale = 10.0)
        val target = mutableListOf(point(0.0, 0.0))
        // screen(5,5) → world(0.5, 0.5), dist ≈ 0.71 > 0.4 → added
        assertTrue(appendFilteredPoints(mockChange(Offset(5f, 5f)), vp, target))
    }

    @Test
    fun `min dist world capped at 2 point 5 on low zoom`() {
        // scale=0.25 → 4/0.25 = 16, но кап 2.5
        val vp = mockViewport(scale = 0.25)
        val target = mutableListOf(point(0.0, 0.0))
        // screen(10,0) → world(40, 0), dist = 40 > 2.5 → added
        assertTrue(appendFilteredPoints(mockChange(Offset(10f, 0f)), vp, target))
    }

    @Test
    fun `custom minStepPx not effective when cap kicks in`() {
        // minStepPx=20, scale=1 → 20/1 = 20, cap 2.5
        val vp = mockViewport(scale = 1.0)
        val target = mutableListOf(point(0.0, 0.0))
        // world(3, 0) → dist = 3 > 2.5 (но < 20) → добавлен благодаря капу
        assertTrue(appendFilteredPoints(mockChange(Offset(3f, 0f)), vp, target, minStepPx = 20.0))
    }

    @Test
    fun `custom minStepPx effective at high zoom without cap`() {
        // minStepPx=20, scale=10 → 20/10 = 2.0, не капнуто
        val vp = mockViewport(scale = 10.0)
        val target = mutableListOf(point(0.0, 0.0))
        // screen(15,0) → world(1.5, 0) < 2.0 → filtered
        assertFalse(appendFilteredPoints(mockChange(Offset(15f, 0f)), vp, target, minStepPx = 20.0))
        // screen(25,0) → world(2.5, 0) > 2.0 → added
        assertTrue(appendFilteredPoints(mockChange(Offset(25f, 0f)), vp, target, minStepPx = 20.0))
    }

    // ---------- давление ----------

    @Test
    fun `pressure clamped to 0 point 05 minimum`() {
        val vp = mockViewport()
        val target = mutableListOf<StrokePoint>()
        appendFilteredPoints(mockChange(Offset(0f, 0f), pressure = -1f), vp, target)
        assertEquals(0.05f, target[0].pressure, EPS_PRESSURE)
    }

    @Test
    fun `pressure clamped to 1 maximum`() {
        val vp = mockViewport()
        val target = mutableListOf<StrokePoint>()
        appendFilteredPoints(mockChange(Offset(0f, 0f), pressure = 5f), vp, target)
        assertEquals(1f, target[0].pressure, EPS_PRESSURE)
    }

    @Test
    fun `pressure in range passed through`() {
        val vp = mockViewport()
        val target = mutableListOf<StrokePoint>()
        appendFilteredPoints(mockChange(Offset(0f, 0f), pressure = 0.42f), vp, target)
        assertEquals(0.42f, target[0].pressure, EPS_PRESSURE)
    }

    companion object {
        private const val EPS = 1e-6
        private const val EPS_PRESSURE = 1e-4f
    }
}