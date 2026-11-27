package com.infinitecanvas.board.data.local.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Тесты AtomicLongIdGenerator.
 *
 * Контракт, на который опирается остальное приложение:
 *   - next() строго возрастает (порядок наложения штрихов держится на id)
 *   - id 0 никогда не выдаётся (он занят «временным» штрихом, рисуемым до коммита)
 *   - seedAtLeast поднимает счётчик выше сохранённых id и не понижает его
 *   - при конкурентных вызовах id не повторяются
 */
class AtomicLongIdGeneratorTest {

    @Test
    fun `next starts at 1 and increases by one`() {
        val gen = AtomicLongIdGenerator()

        assertEquals(1L, gen.next())
        assertEquals(2L, gen.next())
        assertEquals(3L, gen.next())
    }

    @Test
    fun `never returns zero`() {
        val gen = AtomicLongIdGenerator()
        repeat(100) {
            assertTrue("id обязан быть > 0", gen.next() > 0L)
        }
    }

    @Test
    fun `seedAtLeast raises counter so next exceeds seeded value`() {
        val gen = AtomicLongIdGenerator()
        gen.seedAtLeast(500L)

        assertEquals(501L, gen.next())
    }

    @Test
    fun `seedAtLeast never lowers the counter`() {
        val gen = AtomicLongIdGenerator()
        gen.seedAtLeast(500L)
        val afterHigh = gen.next() // 501

        // Повторное сидирование меньшим значением не должно откатить счётчик:
        // иначе выданный id повторился бы.
        gen.seedAtLeast(10L)
        val next = gen.next()

        assertEquals(afterHigh + 1, next)
    }

    @Test
    fun `seedAtLeast is idempotent`() {
        val gen = AtomicLongIdGenerator()
        gen.seedAtLeast(42L)
        gen.seedAtLeast(42L)
        gen.seedAtLeast(42L)

        assertEquals(43L, gen.next())
    }

    @Test
    fun `seedAtLeast zero is a no-op`() {
        val gen = AtomicLongIdGenerator()
        gen.seedAtLeast(0L)

        assertEquals(1L, gen.next())
    }

    @Test
    fun `negative seed is treated as zero`() {
        val gen = AtomicLongIdGenerator()
        gen.seedAtLeast(-100L)

        assertEquals(1L, gen.next())
    }

    @Test
    fun `concurrent next calls produce unique ids`() {
        val gen = AtomicLongIdGenerator()
        val threads = 8
        val perThread = 500
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val results = java.util.Collections.synchronizedList(ArrayList<Long>())

        repeat(threads) {
            pool.execute {
                start.await()
                repeat(perThread) { results.add(gen.next()) }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue("потоки не завершились", done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        // Уникальность — инвариант: дубликат id означал бы потерю штриха в quadtree.
        assertEquals(threads * perThread, results.size)
        assertEquals(results.size, results.toSet().size)
    }
}
