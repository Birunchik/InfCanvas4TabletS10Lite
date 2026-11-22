package com.infinitecanvas.board.presentation.canvas

import com.infinitecanvas.board.presentation.canvas.renderer.ActiveStrokeCache
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты ActiveStrokeCache (ActiveStrokeRenderer.kt).
 *
 * Контракт простой, но критичный для рендера активного штриха:
 *   - initial state: pointCount = -1, geometry = null
 *     (не 0: drawActiveStroke проверяет `cache.pointCount != 0` чтобы
 *      вызвать reset() при переходе к пустому состоянию)
 *   - reset(): pointCount = 0, geometry = null (без восстановления в -1)
 *   - reset() идемпотентен
 *
 * Robolectric: CachedStrokeGeometry содержит androidx.compose.ui.graphics.Path.
 * Мокаем его через MockK, но загрузка класса в тесте требует Android-контекста.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveStrokeCacheTest {

    @Test
    fun `initial state has pointCount minus one and null geometry`() {
        val cache = ActiveStrokeCache()

        assertEquals(
            "Initial -1 (не 0): drawActiveStroke отличает «ещё не рисовали» от «сброшено»",
            -1,
            cache.pointCount,
        )
        assertNull(cache.geometry)
    }

    @Test
    fun `reset sets pointCount to zero`() {
        val cache = ActiveStrokeCache().apply { pointCount = 42 }

        cache.reset()

        assertEquals(0, cache.pointCount)
    }

    @Test
    fun `reset clears geometry`() {
        val cache = ActiveStrokeCache().apply {
            geometry = mockk<CachedStrokeGeometry>(relaxed = true)
        }

        cache.reset()

        assertNull(cache.geometry)
    }

    @Test
    fun `reset is idempotent`() {
        val cache = ActiveStrokeCache()

        cache.reset()
        cache.reset()

        assertEquals(0, cache.pointCount)
        assertNull(cache.geometry)
    }

    @Test
    fun `reset does not touch non-null geometry twice`() {
        // Защита от случайной замены `geometry = null` на что-то вроде `geometry!!.release()`:
        // после первого reset поле должно оставаться null, второй reset не падает
        val cache = ActiveStrokeCache()
        val original = mockk<CachedStrokeGeometry>(relaxed = true)
        cache.geometry = original

        cache.reset()
        assertNull(cache.geometry)

        cache.reset()
        assertNull(cache.geometry)
    }
}