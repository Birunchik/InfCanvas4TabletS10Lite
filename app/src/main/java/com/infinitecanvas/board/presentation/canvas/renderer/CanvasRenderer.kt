package com.infinitecanvas.board.presentation.canvas.renderer

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.presentation.canvas.CachedStrokeGeometry
import androidx.core.graphics.createBitmap

const val HIGHLIGHTER_WIDTH_MULTIPLIER = 6.0f
const val HIGHLIGHTER_CHISEL_ANGLE_DEG = -30f

private val highlighterLayerPaint = Paint()

private val forwardChiselMatrix = Matrix().apply {
    postRotate(-HIGHLIGHTER_CHISEL_ANGLE_DEG)
    postScale(1f, 4.5f)
    postRotate(HIGHLIGHTER_CHISEL_ANGLE_DEG)
}

fun applyChiselForwardTransform(path: Path) {
    path.asAndroidPath().transform(forwardChiselMatrix)
}

// --- Кэш для обычных Stroke (спасает от аллокаций Paint на каждый кадр) ---
private val strokeCache = HashMap<Float, Stroke>()

private fun getStroke(width: Float): Stroke {
    return strokeCache.getOrPut(width) {
        Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
}

// --- Новая текстура карандаша (очень мелкое зерно) ---
private val pencilTextureBitmap: Bitmap by lazy {
    val size = 256
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true // ВАЖНО: сглаживание для мягкой пыли
    }

    val random = java.util.Random(42)
    // 15 000 мелких точек создают эффект плотной графитовой пыли
    for (i in 0 until 15000) {
        val x = random.nextFloat() * size
        val y = random.nextFloat() * size
        val radius = random.nextFloat() * 1.0f + 0.3f
        paint.alpha = random.nextInt(155) + 100 // От 100 до 255 (плотный графит)
        canvas.drawCircle(x, y, radius, paint)
    }
    bitmap
}

private val pencilPaintCache = HashMap<Int, android.graphics.Paint>()
private fun getPencilPaint(colorArgb: Int, strokeWidth: Float, alpha: Float): android.graphics.Paint {
    val cacheKey = colorArgb + (alpha * 1000).toInt()
    return pencilPaintCache.getOrPut(cacheKey) {
        val shader = BitmapShader(pencilTextureBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        android.graphics.Paint().apply {
            this.shader = shader
            this.colorFilter = PorterDuffColorFilter(colorArgb, PorterDuff.Mode.SRC_IN)
            this.style = android.graphics.Paint.Style.STROKE
            this.strokeCap = android.graphics.Paint.Cap.ROUND
            this.strokeJoin = android.graphics.Paint.Join.ROUND
            this.isAntiAlias = true // ВКЛЮЧАЕМ сглаживание
            // Делаем края рваными (шаг 2f, отклонение 0.5f)
            this.pathEffect = android.graphics.DiscretePathEffect(2f, 0.5f)
        }
    }.apply {
        this.strokeWidth = strokeWidth
        this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
    }
}
fun DrawScope.drawCachedGeometry(
    geom: CachedStrokeGeometry,
    visibleWorldBounds: WorldBounds? = null
) {
    val dotCenter = geom.singleDotCenter
    if (dotCenter != null) {
        drawCircle(
            color = geom.color,
            radius = geom.singleDotRadius,
            center = dotCenter
        )
        return
    }

    if (geom.isHighlighter) {
        val layerBounds = Rect(
            left = geom.bounds.minX.toFloat(),
            top = geom.bounds.minY.toFloat(),
            right = geom.bounds.maxX.toFloat(),
            bottom = geom.bounds.maxY.toFloat()
        )
        val opaqueColor = geom.color.copy(alpha = 1f)
        highlighterLayerPaint.alpha = geom.color.alpha

        drawIntoCanvas { canvas ->
            canvas.saveLayer(layerBounds, highlighterLayerPaint)
            try {
                withTransform({
                    rotate(degrees = HIGHLIGHTER_CHISEL_ANGLE_DEG, pivot = Offset.Zero)
                    scale(scaleX = 1f, scaleY = 1f / 4.5f, pivot = Offset.Zero)
                    rotate(degrees = -HIGHLIGHTER_CHISEL_ANGLE_DEG, pivot = Offset.Zero)
                }) {
                    drawPath(
                        path = geom.path,
                        color = opaqueColor,
                        style = Stroke(
                            width = geom.strokeWidth,
                            cap = StrokeCap.Square,
                            join = StrokeJoin.Round
                        )
                    )
                }
            } finally {
                canvas.restore()
            }
        }
        return
    }

    if (geom.variableSegments.isNotEmpty()) {
        for (i in geom.variableSegments.indices) {
            val seg = geom.variableSegments[i]
            if (visibleWorldBounds != null && !seg.bounds.intersects(visibleWorldBounds)) {
                continue
            }
            drawPath(
                path = seg.path,
                color = geom.color,
                style = getStroke(seg.width) // Используем кэш
            )
        }
        return
    }

    // --- Логика для карандаша ---
    if (geom.brushType == BrushType.PENCIL) {
        drawIntoCanvas { canvas ->
            val color = geom.color.toArgb()
            val baseAlpha = geom.color.alpha
            val androidPath = geom.path.asAndroidPath()

            // 1. Широкий и полупрозрачный (создает мягкую тень/края)
            val p1 = getPencilPaint(color, geom.strokeWidth * 1.3f, baseAlpha * 0.4f)
            canvas.nativeCanvas.drawPath(androidPath, p1)

            // 2. Средний (основная текстура)
            val p2 = getPencilPaint(color, geom.strokeWidth * 0.9f, baseAlpha * 0.8f)
            canvas.nativeCanvas.drawPath(androidPath, p2)

            // 3. Узкий и плотный (создает темный центр)
            val p3 = getPencilPaint(color, geom.strokeWidth * 0.5f, baseAlpha)
            canvas.nativeCanvas.drawPath(androidPath, p3)
        }
        return
    }

    // --- Логика для маркера (плоские концы) ---
    if (geom.brushType == BrushType.MARKER) {
        val markerStyle = Stroke(
            width = geom.strokeWidth,
            cap = StrokeCap.Square, // Квадратные концы
            join = StrokeJoin.Bevel  // Более угловатые стыки
        )
        drawPath(
            path = geom.path,
            color = geom.color,
            style = markerStyle
        )
        return
    }

    if (geom.brushType == BrushType.FOUNTAIN) {
        drawPath(
            path = geom.path,
            color = geom.color,
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
        return
    }


    // --- Обычная отрисовка (BALLPOINT, MARKER) ---
    drawPath(
        path = geom.path,
        color = geom.color,
        style = getStroke(geom.strokeWidth) // Используем кэш
    )
}