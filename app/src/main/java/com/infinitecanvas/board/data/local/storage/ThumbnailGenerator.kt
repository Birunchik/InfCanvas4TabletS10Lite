package com.infinitecanvas.board.data.local.storage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.presentation.canvas.calculateWidthForBrush
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap

/**
 * Генерирует лёгкую WebP-миниатюру содержимого доски (480x300, 16:10 под Tab S10 Lite)
 * в фоновом потоке, автоматически центрируя и масштабируя все нарисованные штрихи.
 */
class ThumbnailGenerator(
    private val fileStorage: BoardFileStorage
) {
    suspend fun generateAndSave(
        boardId: Long,
        strokes: List<Stroke>
    ): String? = withContext(Dispatchers.Default) {
        val outFile = fileStorage.getThumbnailFile(boardId)

        if (strokes.isEmpty()) {
            if (outFile.exists()) outFile.delete()
            return@withContext null
        }

        // 1. Вычисляем общий ограничивающий прямоугольник всех штрихов
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

        if (!minX.isFinite() || !minY.isFinite()) return@withContext null

        val contentW = (maxX - minX).coerceAtLeast(32.0)
        val contentH = (maxY - minY).coerceAtLeast(32.0)

        val bitmap = createBitmap(THUMB_WIDTH, THUMB_HEIGHT)
        try {
            val canvas = Canvas(bitmap)
            // Светлый фон холста с лёгкой сеткой
            canvas.drawColor(0xFFF8F9FA.toInt())
            drawSubtleGrid(canvas)

            // 2. Рассчитываем масштаб с отступами по краям (padding)
            val usableW = THUMB_WIDTH - PADDING_PX * 2f
            val usableH = THUMB_HEIGHT - PADDING_PX * 2f
            val scale = min(usableW / contentW.toFloat(), usableH / contentH.toFloat())
                .coerceIn(0.02f, 2.5f)

            val scaledW = contentW.toFloat() * scale
            val scaledH = contentH.toFloat() * scale
            val offsetX = (THUMB_WIDTH - scaledW) * 0.5f - (minX.toFloat() * scale)
            val offsetY = (THUMB_HEIGHT - scaledH) * 0.5f - (minY.toFloat() * scale)

            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)

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
                drawStrokeOnAndroidCanvas(
                    canvas = canvas,
                    stroke = strokes[i],
                    strokePaint = strokePaint,
                    fillPaint = fillPaint,
                    path = reusablePath,
                    invScale = 1f / scale
                )
            }
            canvas.restore()

            // 3. Сохраняем сжатый WebP во временный файл и атомарно заменяем
            withContext(Dispatchers.IO) {
                val tempFile = File(outFile.parentFile, "${outFile.name}.tmp")
                FileOutputStream(tempFile).use { fos ->
                    val format =
                        Bitmap.CompressFormat.WEBP_LOSSY
                    bitmap.compress(format, 80, fos)
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

        val alphaFactor = if (brush.type == BrushType.PENCIL) 0.45f else 1f
        val finalAlpha = ((Color.alpha(brush.colorArgb) * brush.opacity * alphaFactor))
            .toInt()
            .coerceIn(25, 255)
        val colorWithAlpha = (brush.colorArgb and 0x00FFFFFF) or (finalAlpha shl 24)

        // Минимальная видимая толщина на миниатюре, чтобы тонкие линии не пропадали при сильном уменьшении
        val minVisibleWidth = 1.35f * invScale

        if (points.size == 1) {
            val p = points[0]
            fillPaint.color = colorWithAlpha
            val r = max(calculateWidthForBrush(brush, p.pressure) * 0.5f, minVisibleWidth * 0.5f)
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, fillPaint)
            return
        }

        path.reset()
        when (brush.type) {
            BrushType.BALLPOINT, BrushType.PENCIL -> {
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

                val rawWidth = if (brush.type == BrushType.PENCIL) brush.baseSize * 0.75f else brush.baseSize
                strokePaint.color = colorWithAlpha
                strokePaint.strokeWidth = max(rawWidth, minVisibleWidth)
                canvas.drawPath(path, strokePaint)
            }

            BrushType.FOUNTAIN -> {
                buildFountainOutline(points, brush, minVisibleWidth, path)
                fillPaint.color = colorWithAlpha
                canvas.drawPath(path, fillPaint)
            }
        }
    }

    private fun buildFountainOutline(
        points: List<StrokePoint>,
        brush: Brush,
        minWidth: Float,
        outPath: Path
    ) {
        val n = points.size
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

            val halfW = max(calculateWidthForBrush(brush, curr.pressure), minWidth) * 0.5f
            val lx = curr.x.toFloat() - dy * halfW
            val ly = curr.y.toFloat() + dx * halfW
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

            val halfW = max(calculateWidthForBrush(brush, curr.pressure), minWidth) * 0.5f
            val rx = curr.x.toFloat() + dy * halfW
            val ry = curr.y.toFloat() - dx * halfW
            outPath.lineTo(rx, ry)
        }
        outPath.close()
    }

    private companion object {
        const val THUMB_WIDTH = 480
        const val THUMB_HEIGHT = 300
        const val PADDING_PX = 24f
    }
}