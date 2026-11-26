package com.infinitecanvas.board.presentation.canvas


import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.testing.BrushFixtures
import com.infinitecanvas.board.testing.StrokeRealFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты StrokeGeometryCache (StrokeGeometryCache.kt).
 *
 * Покрываемые контракты:
 *   - getOrBuild: hit по (id + bounds), miss при смене bounds
 *   - removeStrokes: удаляет только указанные id, noop для пустого set
 *   - shiftCachedStrokes: сдвигает bounds, noop при (dx=0,dy=0) или пустом set,
 *     не падает на отсутствующих id, сдвигает singleDotCenter
 *   - LRU: max 1500 записей, вытесняется самая старая
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrokeGeometryCacheTest {

    private fun twoPointStroke(
        id: Long,
        x: Double = 0.0,
        y: Double = 0.0,
        brush: com.infinitecanvas.board.domain.model.Brush =
            BrushFixtures.brush(BrushType.BALLPOINT, baseSize = 10f)
    ): Stroke = StrokeRealFixtures.created(id, x to y, x + 10.0 to y + 10.0, brush = brush)

    // ---------- getOrBuild ----------

    @Test
    fun `getOrBuild returns cached instance for same stroke`() {
        val cache = StrokeGeometryCache()
        val s = twoPointStroke(1)

        val g1 = cache.getOrBuild(s)
        val g2 = cache.getOrBuild(s)

        assertSame(g1, g2)
    }

    @Test
    fun `getOrBuild rebuilds when bounds changed`() {
        val cache = StrokeGeometryCache()
        val s = twoPointStroke(1)
        val g1 = cache.getOrBuild(s)

        val sMoved = s.copy(bounds = WorldBounds(100.0, 100.0, 200.0, 200.0))
        val g2 = cache.getOrBuild(sMoved)

        assertNotSame("Смена bounds → пересборка", g1, g2)
    }

    @Test
    fun `getOrBuild different ids produce independent entries`() {
        val cache = StrokeGeometryCache()
        val g1 = cache.getOrBuild(twoPointStroke(1))
        val g2 = cache.getOrBuild(twoPointStroke(2))

        assertNotSame(g1, g2)
        assertSame(g1, cache.getOrBuild(twoPointStroke(1)))
        assertSame(g2, cache.getOrBuild(twoPointStroke(2)))
    }

    // ---------- removeStrokes ----------

    @Test
    fun `removeStrokes removes only specified ids`() {
        val cache = StrokeGeometryCache()
        val s1 = twoPointStroke(1)
        val s2 = twoPointStroke(2)
        val g1Before = cache.getOrBuild(s1)
        val g2 = cache.getOrBuild(s2)

        cache.removeStrokes(setOf(1L))

        // s2 остался — тот же instance
        assertSame(g2, cache.getOrBuild(s2))
        // s1 удалён — при следующем getOrBuild создаётся новый instance.
        // Сравниваем с instance, полученным ДО removeStrokes: раньше тест
        // сравнивал getOrBuild с самим собой (второй вызов — cache hit).
        val g1After = cache.getOrBuild(s1)
        assertNotSame("удалённый id должен быть пересобран заново", g1Before, g1After)
    }

    @Test
    fun `removeStrokes with empty set is noop`() {
        val cache = StrokeGeometryCache()
        val s = twoPointStroke(1)
        val g = cache.getOrBuild(s)

        cache.removeStrokes(emptySet())

        assertSame(g, cache.getOrBuild(s))
    }

    // ---------- shiftCachedStrokes ----------

    @Test
    fun `shiftCachedStrokes updates bounds of cached entry`() {
        val cache = StrokeGeometryCache()
        val s = twoPointStroke(1)
        cache.getOrBuild(s)
        val origBounds = s.bounds

        cache.shiftCachedStrokes(setOf(1L), dx = 10.0, dy = 20.0)

        val translated = s.translated(10.0, 20.0)
        val g = cache.getOrBuild(translated)

        assertEquals(origBounds.minX + 10.0, g.bounds.minX, EPS)
        assertEquals(origBounds.minY + 20.0, g.bounds.minY, EPS)
        assertEquals(origBounds.maxX + 10.0, g.bounds.maxX, EPS)
        assertEquals(origBounds.maxY + 20.0, g.bounds.maxY, EPS)
    }

    @Test
    fun `shiftCachedStrokes with zero delta is noop`() {
        val cache = StrokeGeometryCache()
        val s = twoPointStroke(1)
        val g = cache.getOrBuild(s)

        cache.shiftCachedStrokes(setOf(1L), dx = 0.0, dy = 0.0)

        assertSame("zero delta → bounds не меняются → тот же instance", g, cache.getOrBuild(s))
    }

    @Test
    fun `shiftCachedStrokes with empty ids is noop`() {
        val cache = StrokeGeometryCache()
        val s = twoPointStroke(1)
        val g = cache.getOrBuild(s)

        cache.shiftCachedStrokes(emptySet(), dx = 100.0, dy = 100.0)

        assertSame(g, cache.getOrBuild(s))
    }

    @Test
    fun `shiftCachedStrokes skips missing ids without throwing`() {
        val cache = StrokeGeometryCache()
        // Кэш пуст — сдвиг для несуществующего id не должен падать
        cache.shiftCachedStrokes(setOf(999L), dx = 10.0, dy = 10.0)
    }

    @Test
    fun `shiftCachedStrokes shifts singleDotCenter`() {
        val cache = StrokeGeometryCache()
        val brush = BrushFixtures.brush(BrushType.BALLPOINT)
        val s = StrokeRealFixtures.withExplicitBounds(
            id = 1,
            points = listOf(StrokePoint(50.0, 60.0, 0.5f)),
            brush = brush,
            bounds = WorldBounds(0.0, 0.0, 100.0, 100.0)
        )
        val g = cache.getOrBuild(s)
        assertEquals(50f, g.singleDotCenter!!.x, EPS_F)
        assertEquals(60f, g.singleDotCenter.y, EPS_F)

        cache.shiftCachedStrokes(setOf(1L), dx = 10.0, dy = 20.0)

        val translated = StrokeRealFixtures.withExplicitBounds(
            id = 1,
            points = listOf(StrokePoint(60.0, 80.0, 0.5f)),
            brush = brush,
            bounds = WorldBounds(10.0, 20.0, 110.0, 120.0)
        )
        val gAfter = cache.getOrBuild(translated)

        assertEquals(60f, gAfter.singleDotCenter!!.x, EPS_F)
        assertEquals(80f, gAfter.singleDotCenter.y, EPS_F)
    }

    // ---------- LRU ----------

    @Test
    fun `lru caps size at max entries`() {
        val cache = StrokeGeometryCache()
        for (i in 1L..1501L) cache.getOrBuild(twoPointStroke(i))

        assertEquals(1500, cache.cachedIdsSnapshot().size)
    }

    @Test
    fun `lru evicts oldest entry after exceeding capacity`() {
        val cache = StrokeGeometryCache()
        for (i in 1L..1501L) cache.getOrBuild(twoPointStroke(i))

        val ids = cache.cachedIdsSnapshot()
        assertFalse("id=1 должен быть вытеснен первым", 1L in ids)
        assertTrue("id=1501 должен остаться", 1501L in ids)
        assertTrue("id=1500 должен остаться", 1500L in ids)
    }

    // ---------- helpers ----------
    private companion object {
        const val EPS = 1e-3
        const val EPS_F = 1e-4f
    }
}