package com.infinitecanvas.board.data.local.id

import com.infinitecanvas.board.domain.port.IdGenerator
import java.util.concurrent.atomic.AtomicLong

/**
 * Атомарный монотонный генератор id.
 *
 * Начинается с 1 (0 зарезервирован за «временным» штрихом, который рисуется
 * до коммита и в файл не попадает) и только растёт.
 *
 * Почему не `System.nanoTime()`: тот даёт значения, не связанные с порядком
 * создания и не гарантирующие уникальность при восстановлении из бэкапа или
 * сбросе часов. Порядок наложения штрихов держится на `id`, а коллизия id
 * означает тихую потерю штриха в quadtree (`HashMap` по id).
 */
class AtomicLongIdGenerator : IdGenerator {

    private val counter = AtomicLong(0L)

    override fun next(): Long = counter.incrementAndGet()

    // CAS-цикл, а не `set`: параллельный вызов next() не должен откатить счётчик
    // назад, иначе выданный id повторится. Уникальность — инвариант, поэтому
    // гонку разрешаем в пользу большего значения.
    override fun seedAtLeast(highestUsed: Long) {
        val target = highestUsed.coerceAtLeast(0L)
        while (true) {
            val current = counter.get()
            if (current >= target) return
            if (counter.compareAndSet(current, target)) return
        }
    }
}
