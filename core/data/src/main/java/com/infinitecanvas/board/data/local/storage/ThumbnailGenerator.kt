package com.infinitecanvas.board.data.local.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.calculateWidth
import com.infinitecanvas.board.domain.port.BoardThumbnailer
import com.infinitecanvas.board.domain.port.ImageStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.withTranslation

// Рендер — CPU-работа (Path/Bitmap), поэтому Dispatchers.Default, а запись файла
// уходит в IO. Параллельные вызовы для одной доски делят один .tmp — не запускать.
class ThumbnailGenerator(
    private val fileStorage: BoardFileStorage,
    private val imageStore: ImageStore
) : BoardThumbnailer {
    // null = видимого контента нет (пустая доска), и файл превью при этом удаляется.
    // Вызывающий не должен сохранять null как путь: в БД останется ссылка на удалённый файл.
    override suspend fun generateAndSave(
        boardId: Long,
        strokes: List<Stroke>,
        images: List<BoardImage>
    ): String? = withContext(Dispatchers.Default) {
        val outFile = fileStorage.getThumbnailFile(boardId)
        // Закреплённые изображения живут в экранных координатах и не часть мира —
        // в кадр превью они не входят.
        val worldImages = images.filterNot { it.isPinned }

        if (strokes.isEmpty() && worldImages.isEmpty()) {
            if (outFile.exists()) outFile.delete()
            return@withContext null
        }

        // Кадрируем по содержимому, а не по viewport: превью должно быть читаемым
        // независимо от того, куда был сдвинут холст в момент сохранения.
        // 1. Вычисляем общий WorldBounds по штрихам и незакреплённым изображениям
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        for (i in strokes.indices) {
            val b = strokes[i].bounds
            if (b.minX < minX) minX = b.minX
            if (b.minY < minY) minY = b.minY
            if (b.maxX > maxX) maxX = b.maxX
            if (b.maxY > maxY) maxY = b.maxY
        }
        for (i in worldImages.indices) {
            val b = worldImages[i].bounds
            if (b.minX < minX) minX = b.minX
            if (b.minY < minY) minY = b.minY
            if (b.maxX > maxX) maxX = b.maxX
            if (b.maxY > maxY) maxY = b.maxY
        }

        if (!minX.isFinite() || !minY.isFinite()) return@withContext null

        // Минимум 32 world units защищает от деления на ~0, когда на доске одна
        // точка или крошечный штрих.
        val contentW = (maxX - minX).coerceAtLeast(32.0)
        val contentH = (maxY - minY).coerceAtLeast(32.0)

        // bitmap освобождается в finally: при исключении иначе утечёт native-память,
        // а генерация вызывается часто при автосохранении.
        val bitmap = createBitmap(THUMB_WIDTH, THUMB_HEIGHT)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFFF8F9FA.toInt())
            drawSubtleGrid(canvas)

            val usableW = THUMB_WIDTH - PADDING_PX * 2f
            val usableH = THUMB_HEIGHT - PADDING_PX * 2f
            // contain-вписывание: min по осям, чтобы содержимое влезло целиком.
            // clamp 0.02..2.5 страхует от экстремальных мировых координат.
            val scale = min(usableW / contentW.toFloat(), usableH / contentH.toFloat())
                .coerceIn(0.02f, 2.5f)

            val scaledW = contentW.toFloat() * scale
            val scaledH = contentH.toFloat() * scale
            // Центрируем содержимое в 480×300 и сдвигаем на -min*scale: вместе с
            // translate/scale ниже это и есть отображение world → пиксели превью.
            val offsetX = (THUMB_WIDTH - scaledW) * 0.5f - (minX.toFloat() * scale)
            val offsetY = (THUMB_HEIGHT - scaledH) * 0.5f - (minY.toFloat() * scale)

            canvas.withTranslation(offsetX, offsetY) {
                scale(scale, scale)

                // inSampleSize=4 достаточен для 480 px и экономит память; subBitmap
                // освобождается в finally, иначе при сотнях картинок будет OOM.
                // 2. Сначала рисуем незакреплённые изображения (под штрихами)
                val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
                val dstRect = RectF()
                val sampleOpts = BitmapFactory.Options().apply { inSampleSize = 4 }

                for (i in worldImages.indices) {
                    val img = worldImages[i]
                    val file = imageStore.fileFor(boardId, img.fileName)
                    if (!file.exists()) continue
                    val subBitmap =
                        BitmapFactory.decodeFile(file.absolutePath, sampleOpts) ?: continue
                    try {
                        dstRect.set(
                            img.bounds.minX.toFloat(),
                            img.bounds.minY.toFloat(),
                            img.bounds.maxX.toFloat(),
                            img.bounds.maxY.toFloat()
                        )
                        drawBitmap(subBitmap, null, dstRect, bitmapPaint)
                    } finally {
                        subBitmap.recycle()
                    }
                }

                // Порядок слоёв повторяет холст: изображения под штрихами. Менять
                // порядок нельзя — превью станет непохожим на доску.
                // 3. Поверх изображений рисуем штрихи
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }
                val reusablePath = Path()

                for (i in strokes.indices) {
                    // invScale компенсирует canvas.scale: minVisibleWidth задаётся
                    // в пикселях превью, чтобы тонкие штрихи не исчезали.
                    drawStrokeOnAndroidCanvas(
                        canvas = this,
                        stroke = strokes[i],
                        strokePaint = strokePaint,
                        fillPaint = fillPaint,
                        path = reusablePath,
                        invScale = 1f / scale
                    )
                }
            }

            // Запись — в IO; temp+rename не даёт читателю увидеть недописанный webp.
            // fsync нет, поэтому при сбое питания файл превью можно потерять.
            withContext(Dispatchers.IO) {
                val tempFile = File(outFile.parentFile, "${outFile.name}.tmp")
                FileOutputStream(tempFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, fos)
                    fos.flush()
                }
                if (outFile.exists()) outFile.delete()
                tempFile.renameTo(outFile)
            }

            outFile.absolutePath
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawSubtleGrid(canvas: Canvas) {
        val gridPaint = Paint().apply {
            color = 0xFFE6E9EE.toInt()
            strokeWidth = 1f
        }
        val step = 30f
        var x = step
        while (x < THUMB_WIDTH) {
            canvas.drawLine(x, 0f, x, THUMB_HEIGHT.toFloat(), gridPaint)
            x += step
        }
        var y = step
        while (y < THUMB_HEIGHT) {
            canvas.drawLine(0f, y, THUMB_WIDTH.toFloat(), y, gridPaint)
            y += step
        }
    }

    private fun drawStrokeOnAndroidCanvas(
        canvas: Canvas,
        stroke: Stroke,
        strokePaint: Paint,
        fillPaint: Paint,
        path: Path,
        invScale: Float
    ) {
        val points = stroke.points
        if (points.isEmpty()) return
        val brush = stroke.brush
        val minVisibleWidth = 1.35f * invScale

        // Highlighter рисуется через saveLayerAlpha с непрозрачным цветом: иначе
        // полупрозрачные сегменты складываются и на стыках появляются тёмные пятна.
        if (brush.type == BrushType.HIGHLIGHTER) {
            val layerAlpha = (brush.opacity.coerceIn(0.35f, 0.45f) * 255f)
                .toInt()
                .coerceIn(25, 255)
            val opaqueColor = (brush.colorArgb and 0x00FFFFFF) or (0xFF shl 24)

            if (points.size == 1) {
                val p = points[0]
                fillPaint.color = (brush.colorArgb and 0x00FFFFFF) or (layerAlpha shl 24)
                val r = max(brush.baseSize * 0.5f, minVisibleWidth * 0.5f)
                canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, fillPaint)
                return
            }

            path.reset()
            path.moveTo(points[0].x.toFloat(), points[0].y.toFloat())
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = ((prev.x + curr.x) * 0.5).toFloat()
                val midY = ((prev.y + curr.y) * 0.5).toFloat()
                path.quadTo(prev.x.toFloat(), prev.y.toFloat(), midX, midY)
            }
            val last = points.last()
            path.lineTo(last.x.toFloat(), last.y.toFloat())

            strokePaint.color = opaqueColor
            strokePaint.strokeWidth = max(brush.baseSize, minVisibleWidth)

            val saveCount = canvas.saveLayerAlpha(
                stroke.bounds.minX.toFloat(),
                stroke.bounds.minY.toFloat(),
                stroke.bounds.maxX.toFloat(),
                stroke.bounds.maxY.toFloat(),
                layerAlpha
            )
            try {
                canvas.drawPath(path, strokePaint)
            } finally {
                canvas.restoreToCount(saveCount)
            }
            return
        }

        val finalAlpha = (Color.alpha(brush.colorArgb) * brush.opacity)
            .toInt()
            .coerceIn(25, 255)
        val colorWithAlpha = (brush.colorArgb and 0x00FFFFFF) or (finalAlpha shl 24)

        if (points.size == 1) {
            val p = points[0]
            fillPaint.color = colorWithAlpha
            val r = max(brush.calculateWidth(p.pressure, 0f) * 0.5f, minVisibleWidth * 0.5f)
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, fillPaint)
            return
        }

        path.reset()
        buildVariableWidthOutline(points, brush, minVisibleWidth, path)
        fillPaint.color = colorWithAlpha
        canvas.drawPath(path, fillPaint)
    }

    // Сглаживание pressure/speed — низкочастотный фильтр: без него ширина дёргается
    // от сэмпла к сэмплу. Контур строится обходом точек слева и справа с общей нормалью.
    private fun buildVariableWidthOutline(
        points: List<StrokePoint>,
        brush: Brush,
        minWidth: Float,
        outPath: Path
    ) {
        val n = points.size
        val halfWidths = FloatArray(n)
        var smoothedPressure = points[0].pressure.coerceIn(0.05f, 1f)
        var smoothedSpeed = 0f

        for (i in 0 until n) {
            val curr = points[i]
            val rawPressure = curr.pressure.coerceIn(0.05f, 1f)
            smoothedPressure = if (i == 0) {
                rawPressure
            } else {
                smoothedPressure * 0.68f + rawPressure * 0.32f
            }

            if (brush.type == BrushType.FOUNTAIN && i > 0) {
                val prev = points[i - 1]
                val dist = hypot((curr.x - prev.x).toFloat(), (curr.y - prev.y).toFloat())
                val dt = (curr.timestampMs - prev.timestampMs).coerceAtLeast(1L)
                val instSpeed = if (curr.timestampMs > 0L && prev.timestampMs > 0L) {
                    dist / dt.toFloat()
                } else {
                    dist / 8f
                }
                smoothedSpeed = smoothedSpeed * 0.7f + instSpeed * 0.3f
            }

            halfWidths[i] = max(brush.calculateWidth(smoothedPressure, smoothedSpeed), minWidth) * 0.5f
        }

        for (i in 0 until n) {
            val curr = points[i]
            val prev = if (i > 0) points[i - 1] else curr
            val next = if (i < n - 1) points[i + 1] else curr

            var dx = (next.x - prev.x).toFloat()
            var dy = (next.y - prev.y).toFloat()
            val len = hypot(dx, dy)
            if (len > 1e-4f) {
                dx /= len
                dy /= len
            } else {
                dx = 1f
                dy = 0f
            }

            val hw = halfWidths[i]
            val lx = curr.x.toFloat() - dy * hw
            val ly = curr.y.toFloat() + dx * hw
            if (i == 0) outPath.moveTo(lx, ly) else outPath.lineTo(lx, ly)
        }

        for (i in n - 1 downTo 0) {
            val curr = points[i]
            val prev = if (i > 0) points[i - 1] else curr
            val next = if (i < n - 1) points[i + 1] else curr

            var dx = (next.x - prev.x).toFloat()
            var dy = (next.y - prev.y).toFloat()
            val len = hypot(dx, dy)
            if (len > 1e-4f) {
                dx /= len
                dy /= len
            } else {
                dx = 1f
                dy = 0f
            }

            val hw = halfWidths[i]
            val rx = curr.x.toFloat() + dy * hw
            val ry = curr.y.toFloat() - dx * hw
            outPath.lineTo(rx, ry)
        }
        outPath.close()
    }

    private companion object {
        // 480×300 — целевой размер карточки в UI; менять только вместе с версткой.
        const val THUMB_WIDTH = 480
        const val THUMB_HEIGHT = 300
        const val PADDING_PX = 24f
    }
}