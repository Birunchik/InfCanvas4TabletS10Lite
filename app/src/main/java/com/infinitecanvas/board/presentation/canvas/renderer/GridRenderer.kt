package com.infinitecanvas.board.presentation.canvas.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import kotlin.math.floor
import kotlin.math.round

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

    drawPath(path = minorGridPath, color = gridColor, style = Stroke(width = 1f))
    drawPath(path = majorGridPath, color = majorGridColor, style = Stroke(width = 2f))
}