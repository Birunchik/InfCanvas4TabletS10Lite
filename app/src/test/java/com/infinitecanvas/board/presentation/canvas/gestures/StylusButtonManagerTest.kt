package com.infinitecanvas.board.presentation.canvas.gestures

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.testing.FakeClock
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты StylusButtonManager (StylusButtonManager.kt).
 *
 * Покрываемые контракты:
 *   - одиночный press → eraser, двойной (≤400ms) → lasso
 *   - состояние сбрасывается на release
 *   - счёт идёт по моменту press, не release
 *   - повторный pressed-event без release не триггерит lasso
 *   - secondary button эквивалентна stylus primary
 *   - handlePointerInterop всегда возвращает false
 *   - updateEraserPos / clearEraserPos — side state для hover-превью
 *
 * Robolectric: MotionEvent — финальный Android-класс, но конструировать
 * его не нужно (мокаем), однако загрузка класса требует Android runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StylusButtonManagerTest {

    // ---------- helpers ----------

    private fun createManager(
        clock: FakeClock = FakeClock(),
        activeTool: () -> CanvasTool = { CanvasTool.ERASER },
        onStateChanged: () -> Unit = {}
    ): StylusButtonManager = StylusButtonManager(
        activeTool = activeTool,
        onStateChanged = onStateChanged,
        clock = { clock.nowMs }
    )

    /**
     * Мок MotionEvent: из всего класса используется только buttonState.
     * relaxed=true спасает от NPE на прочих геттерах, если код их случайно
     * тронет при рефакторинге.
     */
    private fun event(buttonState: Int): MotionEvent {
        val e = mockk<MotionEvent>(relaxed = true)
        every { e.buttonState } returns buttonState
        return e
    }

    // ---------- начальное состояние ----------

    @Test
    fun `initial state has both hardware flags false`() {
        val m = createManager()
        assertFalse(m.isHardwareEraserHeld)
        assertFalse(m.isHardwareLassoHeld)
        assertNull(m.eraserScreenPos)
    }

    // ---------- одиночный press ----------

    @Test
    fun `single press sets eraser flag`() {
        val m = createManager()

        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY))

        assertTrue(m.isHardwareEraserHeld)
        assertFalse(m.isHardwareLassoHeld)
    }

    @Test
    fun `secondary button alone counts as press`() {
        val m = createManager()

        m.handlePointerInterop(event(MotionEvent.BUTTON_SECONDARY))

        assertTrue(m.isHardwareEraserHeld)
    }

    @Test
    fun `release clears flags`() {
        val m = createManager()

        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY))
        assertTrue(m.isHardwareEraserHeld)

        m.handlePointerInterop(event(0))

        assertFalse(m.isHardwareEraserHeld)
        assertFalse(m.isHardwareLassoHeld)
    }

    @Test
    fun `release without prior press is noop`() {
        val m = createManager()

        m.handlePointerInterop(event(0))

        assertFalse(m.isHardwareEraserHeld)
        assertFalse(m.isHardwareLassoHeld)
    }

    // ---------- двойной press: 400ms окно ----------

    @Test
    fun `double press within 400ms sets lasso`() {
        val clock = FakeClock(100_000L)
        val m = createManager(clock)

        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY)) // eraser
        m.handlePointerInterop(event(0))                                // release
        clock.advanceBy(200L)                                           // 200ms
        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY)) // lasso

        assertTrue(m.isHardwareLassoHeld)
        assertFalse(m.isHardwareEraserHeld)
    }

    @Test
    fun `double press at exactly 400ms sets lasso`() {
        // Граница включительная: `now - last <= 400` → lasso
        val clock = FakeClock(100_000L)
        val m = createManager(clock)

        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY))
        m.handlePointerInterop(event(0))
        clock.advanceBy(400L)
        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY))

        assertTrue(m.isHardwareLassoHeld)
    }

    @Test
    fun `double press at 401ms is fresh eraser`() {
        val clock = FakeClock(100_000L)
        val m = createManager(clock)

        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY))
        m.handlePointerInterop(event(0))
        clock.advanceBy(401L)
        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY))

        assertTrue(m.isHardwareEraserHeld)
        assertFalse(m.isHardwareLassoHeld)
    }

    @Test
    fun `time window measured from press not release`() {
        // Между press и release много времени — это не влияет на окно;
        // окно считается между двумя press-моментами.
        val clock = FakeClock(100_000L)
        val m = createManager(clock)

        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY)) // press @100_000
        clock.advanceBy(10_000L)                                        // долго держим
        m.handlePointerInterop(event(0))                                // release @110_000
        clock.advanceBy(200L)                                           // press @110_200
        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY))

        // 110_200 - 100_000 = 10_200 > 400 → eraser, а не lasso
        assertTrue(m.isHardwareEraserHeld)
        assertFalse(m.isHardwareLassoHeld)
    }

    // ---------- удержание кнопки без release ----------

    @Test
    fun `repeated pressed event does not re-trigger lasso`() {
        // Пока кнопка удерживается, buttonState не меняется. Повторный вызов
        // с тем же pressed состоянием не должен "думать", что это второй press.
        val clock = FakeClock(100_000L)
        val m = createManager(clock)

        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY)) // eraser
        clock.advanceBy(100L)                                           // <400ms
        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY)) // НЕ lasso

        assertTrue(m.isHardwareEraserHeld)
        assertFalse(m.isHardwareLassoHeld)
    }

    // ---------- полный цикл ----------

    @Test
    fun `press release press release cycles reset state each time`() {
        val clock = FakeClock(100_000L)
        val m = createManager(clock)

        // Cycle 1: eraser
        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY))
        assertTrue(m.isHardwareEraserHeld)
        m.handlePointerInterop(event(0))
        assertFalse(m.isHardwareEraserHeld)

        // Cycle 2 (200ms): lasso
        clock.advanceBy(200L)
        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY))
        assertTrue(m.isHardwareLassoHeld)
        m.handlePointerInterop(event(0))
        assertFalse(m.isHardwareLassoHeld)

        // Cycle 3 (500ms после cycle 2): eraser
        clock.advanceBy(500L)
        m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY))
        assertTrue(m.isHardwareEraserHeld)
        assertFalse(m.isHardwareLassoHeld)
    }

    // ---------- контракт возвращаемого значения ----------

    @Test
    fun `handlePointerInterop always returns false`() {
        // Критично: false → событие доходит до Compose pointerInput.
        // Возврат true съел бы событие и сломал бы рисование.
        val m = createManager()

        assertFalse(m.handlePointerInterop(event(MotionEvent.BUTTON_STYLUS_PRIMARY)))
        assertFalse(m.handlePointerInterop(event(0)))
        assertFalse(m.handlePointerInterop(event(MotionEvent.BUTTON_SECONDARY)))
        assertFalse(m.handlePointerInterop(event(0)))
    }

    // ---------- side state: eraser preview position ----------

    @Test
    fun `updateEraserPos stores position`() {
        val m = createManager()
        m.updateEraserPos(Offset(10f, 20f))
        assertEquals(Offset(10f, 20f), m.eraserScreenPos)
    }

    @Test
    fun `clearEraserPos resets to null`() {
        val m = createManager()
        m.updateEraserPos(Offset(10f, 20f))
        m.clearEraserPos()
        assertNull(m.eraserScreenPos)
    }
}