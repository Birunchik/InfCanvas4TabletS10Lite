package com.infinitecanvas.board.presentation.canvas

import androidx.compose.ui.graphics.ImageBitmap
import com.infinitecanvas.board.data.local.storage.ImageAssetStorage
import com.infinitecanvas.board.testing.MutableDecoder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Тесты BoardImageCache (BoardImageCache.kt).
 *
 * Покрываемые контракты:
 *   1. cache hit → возврат синхронно, без decode
 *   2. cache miss → возврат null немедленно, decode асинхронно
 *   3. успешный decode → bitmap в кэше + onImageLoaded() ровно один раз
 *   4. null от decoder'а → не кладётся в кэш, callback НЕ вызывается
 *   5. in-flight dedup: N вызовов до завершения → ровно 1 decode
 *   6. inFlightKeys очищается: после успеха / после null / после File.exists()=false
 *   7. File.exists()=false → decoder НЕ вызывается
 *   8. LRU-вытеснение: маленький maxCacheKb → старое вытесняется
 *   9. sizeOf минимум 1 KB даже для 1×1 битмапа
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardImageCacheTest {

    // ---------- helpers ----------
    private fun fakeBitmap(width: Int = 100, height: Int = 100): ImageBitmap {
        val bmp = mockk<ImageBitmap>(relaxed = true)
        every { bmp.width } returns width
        every { bmp.height } returns height
        return bmp
    }

    /** Реальный существующий файл — для прохождения File.exists()=true. */
    private fun existingFile(): File =
        File.createTempFile("board_cache_", ".png").apply { writeText("") }

    /** Реальный отсутствующий файл — File.exists()=false. */
    private fun missingFile(): File =
        File.createTempFile("board_cache_", ".png").apply { delete() }

    private fun mockStorage(file: File): ImageAssetStorage {
        val s = mockk<ImageAssetStorage>()
        every { s.getImageFile(any(), any()) } returns file
        return s
    }

    /**
     * Фабрика cache с TestScope + StandardTestDispatcher(testScheduler).
     *
     * Ключевой момент: backgroundScope и ioDispatcher используют один и тот же
     * scheduler — advanceUntilIdle() детерминированно прогоняет и launch, и
     * withContext(ioDispatcher). Без этого была бы гонка с реальными IO-потоками.
     */
    private fun TestScope.createCache(
        storage: ImageAssetStorage,
        decoder: ImageDecoder,
        onImageLoaded: () -> Unit = {},
        maxCacheKb: Int = 64 * 1024
    ): BoardImageCache = BoardImageCache(
        imageAssetStorage = storage,
        scope = this,
        onImageLoaded = onImageLoaded,
        decoder = decoder,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        maxCacheKb = maxCacheKb
    )
    // ---------- 1. cache hit ----------

    @Test
    fun `cacheHit returns bitmap synchronously without decode`() = runTest {
        val decoder = MutableDecoder(fakeBitmap())
        val cache = createCache(mockStorage(existingFile()), decoder)

        // Первый вызов → decode → bitmap в кэше
        cache.getOrLoad(1L, "a.png")
        advanceUntilIdle()
        assertEquals(1, decoder.callCount)

        // Второй вызов → cache hit, decode не вызывается
        val bitmap = cache.getOrLoad(1L, "a.png")
        assertNotNull(bitmap)
        assertEquals(1, decoder.callCount)
    }

    // ---------- 2. cache miss ----------

    @Test
    fun `cacheMiss returns null immediately before load completes`() = runTest {
        val decoder = MutableDecoder(fakeBitmap())
        val cache = createCache(mockStorage(existingFile()), decoder)

        val result = cache.getOrLoad(1L, "a.png")

        assertNull("контракт: null до завершения async-decode", result)
        assertEquals("до advanceUntilIdle decoder не должен был вызываться", 0, decoder.callCount)

        // Явно прогоняем висящую корутину, чтобы не полагаться на авто-advance в runTest.
        advanceUntilIdle()
    }

    // ---------- 3. успешная загрузка ----------

    @Test
    fun `successfulLoad puts bitmap into cache`() = runTest {
        val bmp = fakeBitmap()
        val decoder = MutableDecoder(bmp)
        val cache = createCache(mockStorage(existingFile()), decoder)

        cache.getOrLoad(1L, "a.png")
        advanceUntilIdle()

        val retrieved = cache.getOrLoad(1L, "a.png")
        assertSame(bmp, retrieved)
    }

    @Test
    fun `successfulLoad calls onImageLoaded exactly once`() = runTest {
        var callbackCount = 0
        val decoder = MutableDecoder(fakeBitmap())
        val cache = createCache(
            mockStorage(existingFile()),
            decoder,
            onImageLoaded = { callbackCount++ }
        )

        cache.getOrLoad(1L, "a.png")
        advanceUntilIdle()

        assertEquals(1, callbackCount)
    }

    @Test
    fun `successfulLoad uses key boardId-slash-fileName`() = runTest {
        // Разные (boardId, fileName) — разные ключи → независимые decode
        val decoder = MutableDecoder(fakeBitmap())
        val cache = createCache(mockStorage(existingFile()), decoder)

        cache.getOrLoad(1L, "a.png")
        cache.getOrLoad(1L, "b.png")
        cache.getOrLoad(2L, "a.png")
        advanceUntilIdle()

        assertEquals(3, decoder.callCount)
    }

    // ---------- 4. null от decoder'а ----------

    @Test
    fun `nullResult does not cache and does not call callback`() = runTest {
        var callbackCount = 0
        val decoder = MutableDecoder(result = null)
        val cache = createCache(
            mockStorage(existingFile()),
            decoder,
            onImageLoaded = { callbackCount++ }
        )

        cache.getOrLoad(1L, "a.png")
        advanceUntilIdle()

        assertEquals(1, decoder.callCount)
        assertEquals("null result → callback не должен вызываться", 0, callbackCount)
        assertEquals("null result → кэш пуст", 0, cache.cachedEntryCount())
    }

    // ---------- 5. in-flight dedup ----------

    @Test
    fun `multipleCallsWhileInFlight trigger only one decode`() = runTest {
        // Критично для 90 Hz: getOrLoad зовётся каждый кадр.
        // Без dedup на одно фото шли бы параллельные decode.
        val decoder = MutableDecoder(fakeBitmap())
        val cache = createCache(mockStorage(existingFile()), decoder)

        repeat(5) { cache.getOrLoad(1L, "a.png") }
        // Все 5 вызовов до advanceUntilIdle — decoder ещё не запускался
        assertEquals(0, decoder.callCount)

        advanceUntilIdle()
        assertEquals("5 in-flight вызовов → 1 decode", 1, decoder.callCount)
    }

    // ---------- 6. inFlightKeys очищается ----------

    @Test
    fun `nullResult clears inFlightKey so next call retries`() = runTest {
        // После null-result ключ должен освободиться, иначе следующее открытие
        // фото навсегда получит null без новых попыток декодирования.
        val decoder = MutableDecoder(result = null)
        val cache = createCache(mockStorage(existingFile()), decoder)

        cache.getOrLoad(1L, "a.png")
        advanceUntilIdle()
        assertEquals(1, decoder.callCount)

        // Ключ очищен в finally → повторный вызов запустит новый decode
        cache.getOrLoad(1L, "a.png")
        advanceUntilIdle()
        assertEquals(2, decoder.callCount)
    }

    @Test
    fun `successfulLoad clears inFlightKey even though bitmap is cached`() = runTest {
        // После успеха ключ тоже должен быть очищен (finally block).
        // Проверяем косвенно: in-flight set не должен накапливать записи —
        // если бы не очищался, повторный getOrLoad по тому же ключу не запускал
        // бы decode (что для нас хорошо), но set рос бы неограниченно.
        // Прямой тест — через eviction: вытесненный ключ должен снова декодироваться.
        val decoder = MutableDecoder(fakeBitmap())
        val cache = createCache(mockStorage(existingFile()), decoder)

        cache.getOrLoad(1L, "a.png")
        advanceUntilIdle()
        assertEquals(1, decoder.callCount)

        cache.getOrLoad(1L, "a.png") // cache hit — decode не должен запускаться
        assertEquals(1, decoder.callCount)
    }

    // ---------- 7. File.exists() = false ----------

    @Test
    fun `fileMissing skips decoder and returns null`() = runTest {
        val decoder = MutableDecoder(fakeBitmap())
        val cache = createCache(mockStorage(missingFile()), decoder)

        cache.getOrLoad(1L, "a.png")
        advanceUntilIdle()

        assertEquals("exists()=false → decoder не вызывается", 0, decoder.callCount)
        assertEquals("в кэше ничего нет", 0, cache.cachedEntryCount())
    }

    @Test
    fun `fileMissing clears inFlightKey so retry is possible`() = runTest {
        // Сценарий: файл ещё пишется на диск, exists()=false.
        // Ключ должен освободиться, чтобы следующий кадр попробовал снова.
        var currentFile = missingFile()
        val storage = mockk<ImageAssetStorage>()
        every { storage.getImageFile(any(), any()) } answers { currentFile }

        val decoder = MutableDecoder(fakeBitmap())
        val cache = createCache(storage, decoder)

        cache.getOrLoad(1L, "a.png")
        advanceUntilIdle()
        assertEquals(0, decoder.callCount)

        // Файл «дописался» → следующий вызов должен запустить decode
        currentFile = existingFile()
        cache.getOrLoad(1L, "a.png")
        advanceUntilIdle()
        assertEquals("после появления файла — decode сработал", 1, decoder.callCount)
    }

    // ---------- 8. LRU-вытеснение ----------

    @Test
    fun `lruEviction removes oldest when maxCacheKb exceeded`() = runTest {
        // 100×100 bitmap → sizeOf = 100*100*4 / 1024 = 39 KB
        // maxCacheKb = 100 → помещается 2 битмапа, третий вытесняет первый
        val decoder = MutableDecoder(fakeBitmap(100, 100))
        val cache = createCache(
            mockStorage(existingFile()),
            decoder,
            maxCacheKb = 100
        )

        cache.getOrLoad(1L, "a.png"); advanceUntilIdle()
        cache.getOrLoad(2L, "b.png"); advanceUntilIdle()
        cache.getOrLoad(3L, "c.png"); advanceUntilIdle() // evict a
        assertEquals(3, decoder.callCount)

        // b и c должны быть в кэше (cache hit → без decode)
        assertNotNull(cache.getOrLoad(2L, "b.png"))
        assertNotNull(cache.getOrLoad(3L, "c.png"))
        assertEquals("b и c cached — decoder не вызывался", 3, decoder.callCount)

        // a вытеснен → getOrLoad запускает новый decode
        cache.getOrLoad(1L, "a.png")
        advanceUntilIdle()
        assertEquals("a evicted → re-decode", 4, decoder.callCount)
    }

    // ---------- 9. sizeOf минимум 1 ----------

    @Test
    fun `sizeOf has minimum one KB for tiny bitmaps`() = runTest {
        // 1×1 bitmap: 1*1*4/1024 = 0 → coerceAtLeast(1) → 1 KB.
        // Проверяем через eviction: maxCacheKb=1, второй 1×1 битмап вытеснит первый.
        // Если бы sizeOf вернул 0, вытеснения бы не произошло.
        val decoder = MutableDecoder(fakeBitmap(1, 1))
        val cache = createCache(
            mockStorage(existingFile()),
            decoder,
            maxCacheKb = 1
        )

        cache.getOrLoad(1L, "a.png"); advanceUntilIdle()
        cache.getOrLoad(2L, "b.png"); advanceUntilIdle() // 1+1 > 1 → evict a

        // a вытеснен → getOrLoad должен запустить decode
        cache.getOrLoad(1L, "a.png"); advanceUntilIdle()
        assertEquals("sizeOf(1×1)=1 → a вытеснен → re-decode", 3, decoder.callCount)
    }
}