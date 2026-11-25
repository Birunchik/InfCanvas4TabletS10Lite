package com.infinitecanvas.board.data.local.storage

import android.content.Context
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
  Высокоскоростное бинарное хранилище геометрии досок и метаданных изображений.
 *
 * Формат: DataOutputStream (big-endian), порядок полей фиксирован. Поддерживается
 * только текущая версия раскладки — обратная совместимость удалена.
 */
class BoardFileStorage(context: Context) {

    private val boardsDir = File(context.filesDir, "boards").apply { mkdirs() }
    private val thumbsDir = File(context.filesDir, "thumbnails").apply { mkdirs() }

    data class StoredCanvasPayload(
        val strokes: List<Stroke>,
        val images: List<BoardImage>,
        val viewport: ViewportState
    )

    fun getThumbnailFile(boardId: Long): File = File(thumbsDir, "thumb_$boardId.webp")

    internal fun getBoardFile(boardId: Long): File = File(boardsDir, "board_$boardId.bin")

    // Запись идёт в .tmp и только потом заменяет целевой файл: обрыв не оставит
    // полузаписанный .bin. fsync нет — при потере питания можно потерять последнюю
    // запись, а окно между delete и renameTo не атомарно.
    suspend fun writeBoardPayload(
        boardId: Long,
        strokes: List<Stroke>,
        images: List<BoardImage>,
        viewport: ViewportState
    ) = withContext(Dispatchers.IO) {
        val targetFile = getBoardFile(boardId)
        val tempFile = File(boardsDir, "board_$boardId.tmp")

        DataOutputStream(BufferedOutputStream(FileOutputStream(tempFile), 32 * 1024)).use { out ->
            // Порядок полей — часть контракта файла: viewport → штрихи → изображения.
            // Внутри штриха brush.ordinal пишется Int, поэтому enum нельзя переставлять.
            out.writeInt(MAGIC_HEADER)
            out.writeInt(FORMAT_VERSION)

            // ViewportState
            out.writeDouble(viewport.offsetX)
            out.writeDouble(viewport.offsetY)
            out.writeDouble(viewport.scale)

            // Штрихи
            out.writeInt(strokes.size)
            for (i in strokes.indices) {
                val s = strokes[i]
                out.writeLong(s.id)

                out.writeInt(s.brush.type.ordinal)
                out.writeInt(s.brush.colorArgb)
                out.writeFloat(s.brush.baseSize)
                out.writeFloat(s.brush.opacity)

                out.writeDouble(s.bounds.minX)
                out.writeDouble(s.bounds.minY)
                out.writeDouble(s.bounds.maxX)
                out.writeDouble(s.bounds.maxY)

                val pts = s.points
                out.writeInt(pts.size)
                for (j in pts.indices) {
                    val p = pts[j]
                    out.writeDouble(p.x)
                    out.writeDouble(p.y)
                    out.writeFloat(p.pressure)
                    out.writeLong(p.timestampMs)
                }
            }

            // Изображения
            out.writeInt(images.size)
            for (i in images.indices) {
                val img = images[i]
                out.writeLong(img.id)
                out.writeUTF(img.fileName)
                out.writeDouble(img.bounds.minX)
                out.writeDouble(img.bounds.minY)
                out.writeDouble(img.bounds.maxX)
                out.writeDouble(img.bounds.maxY)
                out.writeInt(img.zIndex)
                out.writeBoolean(img.isPinned)
            }

            out.flush()
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)
    }

    // Неверный MAGIC или несовпадающая версия не бросают исключение, а дают пустую
    // доску: приложение должно открыться даже при повреждённом хранилище.
    // Обрезанный файл так не обрабатывается — чтение упадёт с EOFException.
    suspend fun readBoardPayload(boardId: Long): StoredCanvasPayload = withContext(Dispatchers.IO) {
        val file = getBoardFile(boardId)
        if (!file.exists() || file.length() == 0L) {
            return@withContext StoredCanvasPayload(emptyList(), emptyList(), ViewportState())
        }

        DataInputStream(BufferedInputStream(FileInputStream(file), 32 * 1024)).use { input ->
            val magic = input.readInt()
            if (magic != MAGIC_HEADER) {
                return@withContext StoredCanvasPayload(emptyList(), emptyList(), ViewportState())
            }
            val version = input.readInt()
            if (version != FORMAT_VERSION) {
                return@withContext StoredCanvasPayload(emptyList(), emptyList(), ViewportState())
            }

            val viewport = ViewportState(
                offsetX = input.readDouble(),
                offsetY = input.readDouble(),
                scale = input.readDouble()
            )

            // ordinal из файла может выйти за границы enum (повреждённый файл):
            // coerceIn предотвращает краш ценой подмены типа последним элементом.
            val brushTypes = BrushType.entries
            // coerceIn: без clamp на мусорный счётчик ArrayList(huge) бросит
            // OOM/IllegalCapacity до того, как EOFException поймает мусор.
            val strokeCount = input.readInt().coerceIn(0, MAX_STROKE_COUNT)
            val strokes = ArrayList<Stroke>(strokeCount)

            for (i in 0 until strokeCount) {
                val id = input.readLong()
                val rawTypeOrdinal = input.readInt()
                val colorArgb = input.readInt()
                val baseSize = input.readFloat()
                val opacity = input.readFloat()

                val brush = Brush(
                    type = brushTypes[rawTypeOrdinal.coerceIn(0, brushTypes.lastIndex)],
                    colorArgb = colorArgb,
                    baseSize = baseSize,
                    opacity = opacity
                )

                val bounds = WorldBounds(
                    minX = input.readDouble(),
                    minY = input.readDouble(),
                    maxX = input.readDouble(),
                    maxY = input.readDouble()
                )

                val pointCount = input.readInt().coerceIn(0, MAX_POINT_COUNT)
                val points = ArrayList<StrokePoint>(pointCount)
                for (j in 0 until pointCount) {
                    points.add(
                        StrokePoint(
                            x = input.readDouble(),
                            y = input.readDouble(),
                            pressure = input.readFloat(),
                            timestampMs = input.readLong()
                        )
                    )
                }

                strokes.add(
                    Stroke(
                        id = id,
                        points = points,
                        brush = brush,
                        bounds = bounds
                    )
                )
            }

            val imageCount = input.readInt().coerceIn(0, MAX_IMAGE_COUNT)
            val images = ArrayList<BoardImage>(imageCount)
            for (i in 0 until imageCount) {
                images.add(
                    BoardImage(
                        id = input.readLong(),
                        fileName = input.readUTF(),
                        bounds = WorldBounds(
                            minX = input.readDouble(),
                            minY = input.readDouble(),
                            maxX = input.readDouble(),
                            maxY = input.readDouble()
                        ),
                        zIndex = input.readInt(),
                        isPinned = input.readBoolean()
                    )
                )
            }

            StoredCanvasPayload(strokes = strokes, images = images, viewport = viewport)
        }
    }

    suspend fun deleteBoardFiles(boardId: Long) = withContext(Dispatchers.IO) {
        getBoardFile(boardId).delete()
        getThumbnailFile(boardId).delete()
    }

    // MAGIC — "ICB1" в big-endian. FORMAT_VERSION — единственная поддерживаемая
    // раскладка; файлы с другим номером версии читаются как пустая доска.
    private companion object {
        const val MAGIC_HEADER = 0x49434231 // "ICB1"
        const val FORMAT_VERSION = 1

        // Верхние границы для счётчиков из файла. Реальные доски далеко ниже,
        // но и мусор из повреждённого файла не должен приводить к OOM.
        const val MAX_STROKE_COUNT = 1_000_000
        const val MAX_POINT_COUNT = 1_000_000
        const val MAX_IMAGE_COUNT = 10_000
    }
}