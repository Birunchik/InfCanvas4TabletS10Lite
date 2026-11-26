package com.infinitecanvas.board.presentation.canvas

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.infinitecanvas.board.domain.port.ImageStore
import java.util.Collections
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── НОВОЕ: шов для тестируемости ──
/** Абстракция над BitmapFactory.decodeFile: тест подменяет декодер без Robolectric shadows. */
fun interface ImageDecoder {
    fun decode(file: File): ImageBitmap?
}

class BoardImageCache(
    private val imageStore: ImageStore,
    private val scope: CoroutineScope,
    private val onImageLoaded: () -> Unit,
    // НОВЫЕ параметры с дефолтами — обратная совместимость сохранена:
    private val decoder: ImageDecoder = ImageDecoder { file ->
        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    maxCacheKb: Int = 64 * 1024
) {
    private val cache = object : LruCache<String, ImageBitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return ((value.width.toLong() * value.height.toLong() * 4L) / 1024L)
                .toInt()
                .coerceAtLeast(1)
        }
    }
    private val inFlightKeys = Collections.synchronizedSet(HashSet<String>())
    fun getOrLoad(boardId: Long, fileName: String): ImageBitmap? {
        val key = "$boardId/$fileName"
        cache.get(key)?.let { return it }

        if (inFlightKeys.add(key)) {
            scope.launch {
                try {
                    val loaded = withContext(ioDispatcher) {
                        val file = imageStore.fileFor(boardId, fileName)
                        if (!file.exists()) return@withContext null
                        decoder.decode(file)
                    }
                    if (loaded != null) {
                        cache.put(key, loaded)
                        onImageLoaded()
                    }
                } finally {
                    inFlightKeys.remove(key)
                }
            }
        }
        return null
    }
    @VisibleForTesting
    internal fun cachedEntryCount(): Int = cache.size()
}