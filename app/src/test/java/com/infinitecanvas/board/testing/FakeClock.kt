package com.infinitecanvas.board.testing

/**
 * Управляемые часы для тестов StylusButtonManager (400ms-таймаут).
 *
 * Стартовое значение = 100_000L (не 0): в StylusButtonManager
 * lastButtonPressTimeMs инициализируется нулём, и первый press
 * должен дать eraser (now - 0 > 400). Если FakeClock стартует с 0,
 * первый press ошибочно даст lasso (0 - 0 = 0 <= 400).
 */
class FakeClock(var nowMs: Long = 100_000L) {
    fun advanceBy(ms: Long) {
        require(ms >= 0) { "Время не идёт назад: ms=$ms" }
        nowMs += ms
    }
}