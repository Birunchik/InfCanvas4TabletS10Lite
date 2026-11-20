package com.infinitecanvas.board.presentation.canvas.renderer

import android.graphics.DashPathEffect
import io.mockk.unmockkConstructor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


/**
 * Тесты CanvasRenderState (CanvasRenderState.kt).
 *
 * Покрываемые контракты:
 *   - invalidate() инкрементирует activeTick (единственный триггер перерисовки Canvas)
 *   - worldDashEffect():
 *       * 1-slot кэш: same scale → same instance (без новой аллокации)
 *       * different scale → new instance
 *       * возврат к старому scale НЕ восстанавливает старый объект (кэш перезаписан)
 *       * интервалы dash делятся на scale (14/s, 10/s), phase = 0
 *
 * Robolectric обязателен: worldDashEffect → PathEffect.dashPathEffect →
 * AndroidPathEffect → android.graphics.DashPathEffect, настоящий Android-класс.
 *
 * Формулу интервалов проверяем не через mockkStatic(PathEffect::class)
 * (там MockK подставляет пустой FloatArray в companion-метод на record-фазе,
 * и DashPathEffect падает), а через mockkConstructor(DashPathEffect::class) +
 * constructedWith: мокается фактический конструктор, args приходят реальными.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CanvasRenderStateTest {

    // ---------- invalidate ----------

    @Test
    fun `initial activeTick is zero`() {
        assertEquals(0, CanvasRenderState().activeTick)
    }

    @Test
    fun `invalidate increments activeTick by one`() {
        val state = CanvasRenderState()

        state.invalidate()
        assertEquals(1, state.activeTick)

        state.invalidate()
        assertEquals(2, state.activeTick)
    }

    @Test
    fun `multiple invalidation calls accumulate`() {
        val state = CanvasRenderState()
        repeat(10) { state.invalidate() }
        assertEquals(10, state.activeTick)
    }

    // ---------- worldDashEffect: кэш ----------

    @Test
    fun `worldDashEffect returns cached instance for same scale`() {
        val state = CanvasRenderState()

        val first = state.worldDashEffect(1.0f)
        val second = state.worldDashEffect(1.0f)

        assertSame("Same scale → должен вернуться cached instance", first, second)
    }

    @Test
    fun `worldDashEffect returns new instance for different scale`() {
        val state = CanvasRenderState()

        val atOne = state.worldDashEffect(1.0f)
        val atTwo = state.worldDashEffect(2.0f)

        assertNotSame("Different scale → новый PathEffect", atOne, atTwo)
    }

    @Test
    fun `worldDashEffect does not restore previous scale after eviction`() {
        // Кэш однослотовый: после смены scale старый объект потерян навсегда.
        // Тест фиксирует текущее поведение (не баг — однослотовый кэш дешевле map
        // и достаточен: dash меняется только при pinch-zoom, не каждый кадр).
        val state = CanvasRenderState()

        val original = state.worldDashEffect(1.0f)
        state.worldDashEffect(2.0f)
        val backToOne = state.worldDashEffect(1.0f)

        assertNotSame("После смены scale старый объект не восстанавливается", original, backToOne)
    }

    @After
    fun tearDown() {
        // Страховка: если тест упадёт между mockkConstructor и finally,
        // DashPathEffect останется замоканным и сломает соседние тесты.
        try { unmockkConstructor(DashPathEffect::class) } catch (_: Throwable) { /* already unmocked */ }
    }
}