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
 * Формат: DataOutputStream (big-endian), порядок полей фиксирован. Любое изменение
 * раскладки требует ++FORMAT_VERSION и ветки чтения старых версий.
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

    private fun getBoardFile(boardId: Long): File = File(boardsDir, "board_$boardId.bin")

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

            // Изображения (добавлено в v2, флаг isPinned в v3)
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

    // Неверный MAGIC или версия не бросают исключение, а дают пустую доску:
    // приложение должно открыться даже при повреждённом хранилище.
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
            if (version !in 1..FORMAT_VERSION) {
                return@withContext StoredCanvasPayload(emptyList(), emptyList(), ViewportState())
            }

            val viewport = ViewportState(
                offsetX = input.readDouble(),
                offsetY = input.readDouble(),
                scale = input.readDouble()
            )

            // ordinal из файла может выйти за границы enum (файл новее): coerceIn
            // предотвращает краш ценой подмены типа последним элементом.
            val brushTypes = BrushType.entries
            val strokeCount = input.readInt()
            val strokes = ArrayList<Stroke>(strokeCount)

            for (i in 0 until strokeCount) {
                val id = input.readLong()
                val rawTypeOrdinal = input.readInt()
                val colorArgb = input.readInt()
                val baseSize = input.readFloat()
                val opacity = input.readFloat()

                // В v1..v3 ordinal 2 — PENCIL. Ветка нужна для совместимости: старые
                // файлы читаются как полупрозрачный BALLPOINT, а не как текущий PENCIL.
                // Коэффициент 0.45 подобран под прежний вид и зажат в 0.1..1.
                // Миграция удалённого BrushType.PENCIL (индекс 2 в v1..v3) в полупрозрачный BALLPOINT
                val brush = if (version < 4 && rawTypeOrdinal == 2) {
                    Brush(
                        type = BrushType.BALLPOINT,
                        colorArgb = colorArgb,
                        baseSize = baseSize,
                        opacity = (opacity * 0.45f).coerceIn(0.1f, 1f)
                    )
                } else {
                    Brush(
                        type = brushTypes[rawTypeOrdinal.coerceIn(0, brushTypes.lastIndex)],
                        colorArgb = colorArgb,
                        baseSize = baseSize,
                        opacity = opacity
                    )
                }

                val bounds = WorldBounds(
                    minX = input.readDouble(),
                    minY = input.readDouble(),
                    maxX = input.readDouble(),
                    maxY = input.readDouble()
                )

                val pointCount = input.readInt()
                val points = ArrayList<StrokePoint>(pointCount)
                // В v1..v3 timestamp не писался: 0 означает «нет данных». Потребители
                // (рендер/превью) должны сами подставлять дефолтный dt, а не делить на 0.
                for (j in 0 until pointCount) {
                    points.add(
                        StrokePoint(
                            x = input.readDouble(),
                            y = input.readDouble(),
                            pressure = input.readFloat(),
                            timestampMs = if (version >= 4) input.readLong() else 0L
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

            val images = if (version >= 2) {
                val imageCount = input.readInt()
                ArrayList<BoardImage>(imageCount).apply {
                    for (i in 0 until imageCount) {
                        add(
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
                                isPinned = if (version >= 3) input.readBoolean() else false
                            )
                        )
                    }
                }
            } else {
                emptyList()
            }

            StoredCanvasPayload(strokes = strokes, images = images, viewport = viewport)
        }
    }

    suspend fun deleteBoardFiles(boardId: Long) = withContext(Dispatchers.IO) {
        getBoardFile(boardId).delete()
        getThumbnailFile(boardId).delete()
    }

    // MAGIC — "ICB1" в big-endian. FORMAT_VERSION увеличивается при смене
    // раскладки; чтение поддерживает 1..текущую.
    private companion object {
        const val MAGIC_HEADER = 0x49434231 // "ICB1"
        const val FORMAT_VERSION = 4
    }
}