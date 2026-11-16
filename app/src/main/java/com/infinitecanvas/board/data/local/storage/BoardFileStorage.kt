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

    suspend fun writeBoardPayload(
        boardId: Long,
        strokes: List<Stroke>,
        images: List<BoardImage>,
        viewport: ViewportState
    ) = withContext(Dispatchers.IO) {
        val targetFile = getBoardFile(boardId)
        val tempFile = File(boardsDir, "board_$boardId.tmp")

        DataOutputStream(BufferedOutputStream(FileOutputStream(tempFile), 32 * 1024)).use { out ->
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

            val brushTypes = BrushType.entries
            val strokeCount = input.readInt()
            val strokes = ArrayList<Stroke>(strokeCount)

            for (i in 0 until strokeCount) {
                val id = input.readLong()
                val typeOrdinal = input.readInt().coerceIn(0, brushTypes.lastIndex)
                val brush = Brush(
                    type = brushTypes[typeOrdinal],
                    colorArgb = input.readInt(),
                    baseSize = input.readFloat(),
                    opacity = input.readFloat()
                )

                val bounds = WorldBounds(
                    minX = input.readDouble(),
                    minY = input.readDouble(),
                    maxX = input.readDouble(),
                    maxY = input.readDouble()
                )

                val pointCount = input.readInt()
                val points = ArrayList<StrokePoint>(pointCount)
                for (j in 0 until pointCount) {
                    points.add(
                        StrokePoint(
                            x = input.readDouble(),
                            y = input.readDouble(),
                            pressure = input.readFloat()
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
                                isPinned = version >= 3 && input.readBoolean()
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

    private companion object {
        const val MAGIC_HEADER = 0x49434231 // "ICB1"
        const val FORMAT_VERSION = 3
    }
}