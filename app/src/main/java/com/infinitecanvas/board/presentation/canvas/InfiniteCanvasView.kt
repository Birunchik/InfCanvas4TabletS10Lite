package com.infinitecanvas.board.presentation.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.infinitecanvas.board.domain.model.ViewportState
import kotlin.math.floor
import kotlin.math.abs

@Composable
fun InfiniteCanvasView(
    viewportState: ViewportState,
    onTransform: (panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFF8F9FA),
    gridColor: Color = Color(0xFFE0E3E7),
    majorGridColor: Color = Color(0xFFC4C9D0)
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    onTransform(pan.x, pan.y, zoom, centroid.x, centroid.y)
                }
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Фиксированный шаг сетки в мировых единицах (не зависит от viewportState.scale)
        val fixedStep = 50.0
        val majorStep = fixedStep * 5.0

        // Видимые границы мира в мировых координатах
        val worldLeft = viewportState.screenToWorldX(0f)
        val worldTop = viewportState.screenToWorldY(0f)
        val worldRight = viewportState.screenToWorldX(canvasWidth)
        val worldBottom = viewportState.screenToWorldY(canvasHeight)

        // Первая видимая вертикальная и горизонтальная линии сетки
        val firstGridX = floor(worldLeft / fixedStep) * fixedStep
        val firstGridY = floor(worldTop / fixedStep) * fixedStep

        // Отрисовка вертикальных линий
        var currentX = firstGridX
        while (currentX <= worldRight) {
            val screenX = viewportState.worldToScreenX(currentX)
            val isMajor = abs(currentX % majorStep) < (fixedStep * 0.01) ||
                    abs((currentX % majorStep) - majorStep) < (fixedStep * 0.01)

            drawLine(
                color = if (isMajor) majorGridColor else gridColor,
                start = Offset(screenX, 0f),
                end = Offset(screenX, canvasHeight),
                strokeWidth = if (isMajor) 2f else 1f
            )
            currentX += fixedStep
        }

        // Отрисовка горизонтальных линий
        var currentY = firstGridY
        while (currentY <= worldBottom) {
            val screenY = viewportState.worldToScreenY(currentY)
            val isMajor = abs(currentY % majorStep) < (fixedStep * 0.01) ||
                    abs((currentY % majorStep) - majorStep) < (fixedStep * 0.01)

            drawLine(
                color = if (isMajor) majorGridColor else gridColor,
                start = Offset(0f, screenY),
                end = Offset(canvasWidth, screenY),
                strokeWidth = if (isMajor) 2f else 1f
            )
            currentY += fixedStep
        }

        // Отрисовка маркера начала координат (0, 0)
        val originScreenX = viewportState.worldToScreenX(0.0)
        val originScreenY = viewportState.worldToScreenY(0.0)
        if (originScreenX in -50f..(canvasWidth + 50f) && originScreenY in -50f..(canvasHeight + 50f)) {
            val markerRadius = 16f
            drawLine(
                color = Color(0xFFE53935),
                start = Offset(originScreenX - markerRadius, originScreenY),
                end = Offset(originScreenX + markerRadius, originScreenY),
                strokeWidth = 3f
            )
            drawLine(
                color = Color(0xFFE53935),
                start = Offset(originScreenX, originScreenY - markerRadius),
                end = Offset(originScreenX, originScreenY + markerRadius),
                strokeWidth = 3f
            )
        }
    }
}