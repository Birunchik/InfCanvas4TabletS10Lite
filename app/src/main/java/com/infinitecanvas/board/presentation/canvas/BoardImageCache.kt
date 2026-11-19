package com.infinitecanvas.board.presentation.canvas

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.infinitecanvas.board.data.local.storage.ImageAssetStorage
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ограниченный по памяти (64 МБ) кэш ImageBitmap для плавной 90 Гц отрисовки
 * фотографий на бесконечном холсте Galaxy Tab S10 Lite.
 */
class BoardImageCache(
    private val imageAssetStorage: ImageAssetStorage,
    private val scope: CoroutineScope,
    private val onImageLoaded: () -> Unit
) {
    // Лимит 64 МБ в килобайтах
    private val maxCacheKb = 64 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            // ARGB_8888 = 4 байта на пиксель
            return ((value.width.toLong() * value.height.toLong() * 4L) / 1024L)
                .toInt()
                .coerceAtLeast(1)
        }
    }

    // Дедуп in-flight: getOrLoad зовётся каждый кадр, и без него на одно фото шли бы параллельные decode.
    private val inFlightKeys = Collections.synchronizedSet(HashSet<String>())

    /**
     * Возвращает ImageBitmap мгновенно, если он уже в памяти,
     * либо запускает асинхронную загрузку с диска и возвращает null до завершения.
     */
    fun getOrLoad(boardId: Long, fileName: String): ImageBitmap? {
        val key = "$boardId/$fileName"
        cache.get(key)?.let { return it }

        // null — нормальный контракт: decode идёт в IO, кадр не блокируется, перерисовку инициирует onImageLoaded.
        // Минус: ошибка и отсутствие файла не кэшируются, поэтому такое фото передекодируется каждый кадр.
        if (inFlightKeys.add(key)) {
            scope.launch {
                try {
                    val loaded = withContext(Dispatchers.IO) {
                        val file = imageAssetStorage.getImageFile(boardId, fileName)
                        if (!file.exists()) return@withContext null
                        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
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
}