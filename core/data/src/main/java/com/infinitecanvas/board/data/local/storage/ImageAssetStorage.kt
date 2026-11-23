package com.infinitecanvas.board.data.local.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.domain.port.ImportedImage
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale

/**
 * Управляет импортом изображений из галереи или камеры во внутреннее хранилище доски
 * с обязательным даунсэмплингом до 1600 px и учётом EXIF-ориентации.
 *
 * Все операции с файлами — на Dispatchers.IO: decode/compress блокируют поток.
 * Локальная копия нужна, потому что доступ к content Uri живёт не вечно.
 */
class ImageAssetStorage(private val context: Context) : ImageStore {

    private val assetsRootDir = File(context.filesDir, "board_assets").apply { mkdirs() }



    private fun getBoardAssetsDir(boardId: Long): File =
        File(assetsRootDir, "board_$boardId").apply { mkdirs() }

    override fun fileFor(boardId: Long, fileName: String): File =
        File(getBoardAssetsDir(boardId), fileName)

    /**
     * Создаёт временный файл в cacheDir для съёмки фото через ActivityResultContracts.TakePicture.
     */
    override fun createTempCameraFile(): File {
        val cameraDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
        return File(cameraDir, "capture_${System.nanoTime()}.jpg")
    }

    /**
     * Импортирует изображение по Uri (из галереи или камеры), сжимает и сохраняет в папку доски.
     *
     * Порядок критичен: сначала копируем Uri в cache, пока permission валиден,
     * и только потом decodeFile. Повторное открытие Uri после рестарта может упасть.
     */
    override suspend fun importFromUri(boardId: Long, uri: Uri): ImportedImage? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver

            // 1. Создаем локальную временную копию из Uri, пока права на чтение активны
            val tempInputFile = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}.tmp")
            try {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempInputFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    Log.e("ImageAssetStorage", "Failed to open input stream for uri: $uri")
                    return@withContext null
                }
            } catch (e: SecurityException) {
                Log.e("ImageAssetStorage", "No permission to read uri: $uri", e)
                return@withContext null
            } catch (e: Exception) {
                Log.e("ImageAssetStorage", "Failed to copy uri to cache: $uri", e)
                return@withContext null
            }

            try {
                // inJustDecodeBounds читает только заголовок: bitmap не аллоцируется,
                // поэтому размеры можно узнать до решения о inSampleSize.
                // 2. Читаем размеры из локального временного файла
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(tempInputFile.absolutePath, boundsOptions)

                val srcWidth = boundsOptions.outWidth
                val srcHeight = boundsOptions.outHeight
                if (srcWidth <= 0 || srcHeight <= 0) return@withContext null

                // EXIF читаем из локальной копии, а не из потока Uri: файл можно
                // переоткрыть. Поддержаны только повороты 90/180/270, не зеркала.
                // 3. Читаем EXIF-поворот из временного файла
                val rotationDegrees = try {
                    val exif = ExifInterface(tempInputFile.absolutePath)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (_: Exception) {
                    0
                }

                // inSampleSize — только степень двойки, поэтому декод может быть
                // чуть больше 1600 px; точный размер доводится scale() ниже.
                // 4. Декодируем с оптимальным inSampleSize
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(srcWidth, srcHeight, MAX_DIMENSION_PX)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                // null — битый/неподдерживаемый файл: выходим, temp удалится в finally.
                var processedBitmap = BitmapFactory.decodeFile(tempInputFile.absolutePath, decodeOptions)
                    ?: return@withContext null

                try {
                    // Поворачиваем согласно EXIF, если нужно
                    // createBitmap с Matrix может вернуть исходный объект, поэтому
                    // старый bitmap освобождаем только при другом instance.
                    if (rotationDegrees != 0) {
                        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        val rotated = Bitmap.createBitmap(
                            processedBitmap,
                            0,
                            0,
                            processedBitmap.width,
                            processedBitmap.height,
                            matrix,
                            true
                        )
                        if (rotated !== processedBitmap) {
                            processedBitmap.recycle()
                            processedBitmap = rotated
                        }
                    }

                    // Точное масштабирование до MAX_DIMENSION_PX по длинной стороне
                    val maxSide = max(processedBitmap.width, processedBitmap.height)
                    // Точный ресайз после поворота: EXIF мог поменять местами стороны,
                    // поэтому maxSide считается уже у повёрнутого bitmap.
                    if (maxSide > MAX_DIMENSION_PX) {
                        val scale = MAX_DIMENSION_PX.toFloat() / maxSide.toFloat()
                        val targetW = (processedBitmap.width * scale).roundToInt().coerceAtLeast(1)
                        val targetH = (processedBitmap.height * scale).roundToInt().coerceAtLeast(1)
                        val scaled = processedBitmap.scale(targetW, targetH)
                        if (scaled !== processedBitmap) {
                            processedBitmap.recycle()
                            processedBitmap = scaled
                        }
                    }

                    val fileName = "img_${System.nanoTime()}.webp"
                    val targetFile = fileFor(boardId, fileName)
                    val tempTargetFile = File(targetFile.parentFile, "$fileName.tmp")

                    FileOutputStream(tempTargetFile).use { fos ->
                        val format = Bitmap.CompressFormat.WEBP_LOSSY
                        processedBitmap.compress(format, 85, fos)
                        fos.flush()
                    }
                    if (targetFile.exists()) targetFile.delete()
                    tempTargetFile.renameTo(targetFile)

                    ImportedImage(
                        fileName = fileName,
                        widthPx = processedBitmap.width,
                        heightPx = processedBitmap.height
                    )
                } finally {
                    // Bitmap живёт в native heap: без recycle память не вернётся сразу.
                    // Проверка isRecycled защищает от повторного освобождения.
                    if (!processedBitmap.isRecycled) {
                        processedBitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageAssetStorage", "Unexpected error processing image file", e)
                null
            } finally {
                // Обязательно удаляем временный файл-источник
                if (tempInputFile.exists()) {
                    tempInputFile.delete()
                }
            }
        }

    override suspend fun delete(boardId: Long, fileName: String) = withContext(Dispatchers.IO) {
        fileFor(boardId, fileName).delete()
    }

    override suspend fun deleteAll(boardId: Long) = withContext(Dispatchers.IO) {
        val dir = File(assetsRootDir, "board_$boardId")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }
    // Уменьшаем вдвое, пока следующее деление не станет меньше reqMaxSide:
    // минимальный inSampleSize, при котором длинная сторона ещё >= цели.
    private fun calculateInSampleSize(width: Int, height: Int, reqMaxSide: Int): Int {
        var inSampleSize = 1
        val maxSide = max(width, height)
        while (maxSide / (inSampleSize * 2) >= reqMaxSide) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private companion object {
        const val MAX_DIMENSION_PX = 1600
    }
}