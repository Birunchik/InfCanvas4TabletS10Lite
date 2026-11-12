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
import kotlin.math.log2
import kotlin.math.pow

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
                // Обработка мультитач-жестов панорамирования и масштабирования
                detectTransformGestures { centroid, pan, zoom, _ ->
                    onTransform(pan.x, pan.y, zoom, centroid.x, centroid.y)
                }
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val scale = viewportState.scale

        // Базовый шаг сетки в мировых единицах (50px при 100% масштабе)
        val baseGridStep = 50.0

        // Адаптивный шаг сетки: удваиваем или делим шаг на 2, чтобы на экране ячейки всегда были 25..100dp
        val scaleExponent = floor(log2(scale)).toInt()
        val adaptiveStep = baseGridStep / 2.0.pow(scaleExponent.toDouble())
        val majorStep = adaptiveStep * 5.0

        // Видимые границы мира в мировых координатах
        val worldLeft = viewportState.screenToWorldX(0f)
        val worldTop = viewportState.screenToWorldY(0f)
        val worldRight = viewportState.screenToWorldX(canvasWidth)
        val worldBottom = viewportState.screenToWorldY(canvasHeight)

        // Первая видимая вертикальная и горизонтальная линии сетки
        val firstGridX = floor(worldLeft / adaptiveStep) * adaptiveStep
        val firstGridY = floor(worldTop / adaptiveStep) * adaptiveStep

        // Отрисовка вертикальных линий (без аллокаций объектов внутри цикла)
        var currentX = firstGridX
        while (currentX <= worldRight) {
            val screenX = viewportState.worldToScreenX(currentX)
            val isMajor = kotlin.math.abs(currentX % majorStep) < (adaptiveStep * 0.01) ||
                    kotlin.math.abs((currentX % majorStep) - majorStep) < (adaptiveStep * 0.01)

            drawLine(
                color = if (isMajor) majorGridColor else gridColor,
                start = Offset(screenX, 0f),
                end = Offset(screenX, canvasHeight),
                strokeWidth = if (isMajor) 2f else 1f
            )
            currentX += adaptiveStep
        }

        // Отрисовка горизонтальных линий
        var currentY = firstGridY
        while (currentY <= worldBottom) {
            val screenY = viewportState.worldToScreenY(currentY)
            val isMajor = kotlin.math.abs(currentY % majorStep) < (adaptiveStep * 0.01) ||
                    kotlin.math.abs((currentY % majorStep) - majorStep) < (adaptiveStep * 0.01)

            drawLine(
                color = if (isMajor) majorGridColor else gridColor,
                start = Offset(0f, screenY),
                end = Offset(canvasWidth, screenY),
                strokeWidth = if (isMajor) 2f else 1f
            )
            currentY += adaptiveStep
        }

        // Отрисовка маркера начала мировых координат (0, 0) для ориентира
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