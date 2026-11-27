package com.infinitecanvas.board.testing

import com.infinitecanvas.board.domain.port.IdGenerator

/**
 * Детерминированный [IdGenerator] для тестов: те же гарантии, что у боевой
 * реализации (строго растёт, seedAtLeast не понижает, 0 зарезервирован),
 * но без атомики и без скрытого состояния между тестами.
 *
 * Нужен вместо реального nanoTime, потому что тестам важна ВОСПРОИЗВОДИМОСТЬ id:
 * на них проверяется порядок наложения и уникальность после paste.
 */
class FakeIdGenerator(startAt: Long = 0L) : IdGenerator {

    private var current: Long = startAt.coerceAtLeast(0L)

    /** Сколько раз выдали id — удобно для проверки «новый объект получил новый id». */
    var issuedCount: Int = 0
        private set

    override fun next(): Long {
        issuedCount++
        current += 1
        return current
    }

    override fun seedAtLeast(highestUsed: Long) {
        if (highestUsed > current) current = highestUsed
    }

    /** Текущее значение счётчика (последний выданный id), без выдачи нового. */
    fun peekCurrent(): Long = current

    /** Сброс счётчика: нужен тестам, которые имитируют новый процесс. */
    fun reset(to: Long = 0L) {
        current = to.coerceAtLeast(0L)
        issuedCount = 0
    }
}
