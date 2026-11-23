package com.infinitecanvas.board.presentation.canvas.renderer

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
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.presentation.canvas.CachedStrokeGeometry
import com.infinitecanvas.board.domain.model.BrushDefaults.HIGHLIGHTER_CHISEL_ANGLE_DEG



// Общий Paint для saveLayer: его alpha мутируется перед каждым highlighter'ом,
// поэтому опираться на это поле между вызовами нельзя.
private val highlighterLayerPaint = Paint()

// Chisel-матрица: растягивает путь по Y в 4.5 раза в системе, повёрнутой на -30°,
// а при отрисовке масштаб сжимается обратно той же парой поворотов.
private val forwardChiselMatrix = Matrix().apply {
    postRotate(-HIGHLIGHTER_CHISEL_ANGLE_DEG)
    postScale(1f, 4.5f)
    postRotate(HIGHLIGHTER_CHISEL_ANGLE_DEG)
}

fun applyChiselForwardTransform(path: Path) {
    path.asAndroidPath().transform(forwardChiselMatrix)
}

// --- Кэш для обычных Stroke (спасает от аллокаций Paint на каждый кадр) ---
// Ключ — ширина: она своя у каждого штриха, поэтому HashMap, а не одно поле.
private val strokeCache = HashMap<Float, Stroke>()

private fun getStroke(width: Float): Stroke {
    return strokeCache.getOrPut(width) {
        Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
}

fun DrawScope.drawCachedGeometry(
    geom: CachedStrokeGeometry,
    visibleWorldBounds: WorldBounds? = null
) {
    val dotCenter = geom.singleDotCenter
    if (dotCenter != null) {
        // Точка рисуется кружком: Path у неё пустой, и без раннего возврата мы бы попали в дорогие ветки впустую.
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

        // saveLayer на каждый highlighter дорог (отдельный слой), но без него полупрозрачный штрих
        // темнел бы на самопересечениях: рисуем непрозрачным цветом в слой с alpha.
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