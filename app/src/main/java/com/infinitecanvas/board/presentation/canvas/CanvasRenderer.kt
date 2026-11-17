package com.infinitecanvas.board.presentation.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds
import kotlin.math.floor
import kotlin.math.round

private val highlighterLayerPaint = Paint()

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
                style = Stroke(
                    width = seg.width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        return
    }

    drawPath(
        path = geom.path,
        color = geom.color,
        style = Stroke(
            width = geom.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

fun DrawScope.drawBatchedWorldGrid(
    viewportState: ViewportState,
    worldLeft: Double,
    worldTop: Double,
    worldRight: Double,
    worldBottom: Double,
    canvasWidth: Float,
    canvasHeight: Float,
    gridColor: Color,
    majorGridColor: Color,
    minorGridPath: Path,
    majorGridPath: Path
) {
    val fixedStep = 50.0
    val majorStep = fixedStep * 5.0

    val visibleColumns = (worldRight - worldLeft) / fixedStep
    val effectiveStep = if (visibleColumns > 250.0) majorStep else fixedStep

    val firstGridX = floor(worldLeft / effectiveStep) * effectiveStep
    val firstGridY = floor(worldTop / effectiveStep) * effectiveStep

    minorGridPath.reset()
    majorGridPath.reset()

    // --- Отрисовка вертикальных линий (по оси X) ---
    var currentX = firstGridX
    while (currentX <= worldRight) {
        val screenX = viewportState.worldToScreenX(currentX)

        val gridIndexX = round(currentX / fixedStep).toInt()
        val isMajorX = gridIndexX % 5 == 0

        val target = if (isMajorX) majorGridPath else minorGridPath
        target.moveTo(screenX, 0f)
        target.lineTo(screenX, canvasHeight)
        currentX += effectiveStep
    }

    // --- Отрисовка горизонтальных линий (по оси Y) ---
    var currentY = firstGridY
    while (currentY <= worldBottom) {
        val screenY = viewportState.worldToScreenY(currentY)

        val gridIndexY = round(currentY / fixedStep).toInt()
        val isMajorY = gridIndexY % 5 == 0

        val target = if (isMajorY) majorGridPath else minorGridPath
        target.moveTo(0f, screenY)
        target.lineTo(canvasWidth, screenY)
        currentY += effectiveStep
    }

    drawPath(
        path = minorGridPath,
        color = gridColor,
        style = Stroke(width = 1f)
    )
    drawPath(
        path = majorGridPath,
        color = majorGridColor,
        style = Stroke(width = 2f)
    )
}