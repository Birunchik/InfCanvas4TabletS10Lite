package com.infinitecanvas.board.presentation.canvas.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.presentation.canvas.CanvasGestureHandler

// Всё в screen-space и после withTransform: оверлеи не должны масштабироваться,
// поэтому world→screen считаем вручную и не переиспользуем трансформ холста.
//
// Цвета — топ-левел val: Color — value-класс, но создание на каждом кадре
// всё равно work; выносим, чтобы draw-фаза их не конструировала.
private val ColorSelectionBlue = Color(0xFF1B6EF3)
private val ColorSelectionFill = Color(0x141B6EF3)
private val ColorSelectionBorder = Color(0xFF276DE8)
private val ColorEraserCursor = Color(0x33838383)
private val ColorEraserCursorBorder = Color(0xFF6E6E6E)

internal fun DrawScope.drawScreenOverlays(
    viewportState: ViewportState,
    gestureHandler: CanvasGestureHandler,
    selectedImageId: Long?,
    images: List<BoardImage>,
    selectionBounds: WorldBounds?,
    selectedStrokeIds: Set<Long>,
    screenDashEffect: PathEffect,
    imageHandleCorners: FloatArray
) {
    // 1. HUD выделенной фотографии (рамка + маркеры)
    if (selectedImageId != null) {
        val selImg = images.firstOrNull { it.id == selectedImageId }
        if (selImg != null) {
            val b = if (gestureHandler.previewImageId == selImg.id &&
                gestureHandler.previewImageBounds != null
            ) {
                gestureHandler.previewImageBounds!!
            } else {
                selImg.bounds
            }
            // Pinned-bounds уже в screen px, обычные — в world: поэтому worldToScreen только для незакреплённых.
            val left = if (selImg.isPinned) b.minX.toFloat() else viewportState.worldToScreenX(b.minX)
            val top = if (selImg.isPinned) b.minY.toFloat() else viewportState.worldToScreenY(b.minY)
            val right = if (selImg.isPinned) b.maxX.toFloat() else viewportState.worldToScreenX(b.maxX)
            val bottom = if (selImg.isPinned) b.maxY.toFloat() else viewportState.worldToScreenY(b.maxY)

            drawRect(
                color = ColorSelectionBlue,
                topLeft = Offset(left, top),
                size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
                style = Stroke(width = 2.dp.toPx())
            )

            // Углы пишем в переданный буфер вместо listOf(Offset...): см. KDoc параметра.
            imageHandleCorners[0] = left
            imageHandleCorners[1] = top
            imageHandleCorners[2] = right
            imageHandleCorners[3] = top
            imageHandleCorners[4] = left
            imageHandleCorners[5] = bottom
            imageHandleCorners[6] = right
            imageHandleCorners[7] = bottom

            val handleRadius = 7.dp.toPx()
            val handleStroke = 2.5.dp.toPx()
            var i = 0
            while (i < imageHandleCorners.size) {
                val cornerOffset = Offset(imageHandleCorners[i], imageHandleCorners[i + 1])
                drawCircle(color = Color.White, radius = handleRadius, center = cornerOffset)
                drawCircle(
                    color = ColorSelectionBlue,
                    radius = handleRadius,
                    center = cornerOffset,
                    style = Stroke(width = handleStroke)
                )
                i += 2
            }
        }
    }

    // 2. HUD лассо-выделения штрихов
    if (selectionBounds != null && selectedStrokeIds.isNotEmpty()) {
        // К bounds прибавляем текущий драг: модель ещё не обновлена, а рамка должна ехать вместе со штрихами.
        val left = viewportState.worldToScreenX(selectionBounds.minX + gestureHandler.selectionDragDx)
        val top = viewportState.worldToScreenY(selectionBounds.minY + gestureHandler.selectionDragDy)
        val right = viewportState.worldToScreenX(selectionBounds.maxX + gestureHandler.selectionDragDx)
        val bottom = viewportState.worldToScreenY(selectionBounds.maxY + gestureHandler.selectionDragDy)
        val boxSize = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f))

        drawRect(
            color = ColorSelectionFill,
            topLeft = Offset(left, top),
            size = boxSize
        )
        drawRect(
            color = ColorSelectionBorder,
            topLeft = Offset(left, top),
            size = boxSize,
            style = Stroke(width = 2f, pathEffect = screenDashEffect)
        )
    }

    // 3. Курсор ластика
    val cursor = gestureHandler.eraserScreenPos
    // Радиус 22 px — визуальный ориентир, а не точная зона: world-радиус ластика равен 18/scale.
    if (cursor != null) {
        drawCircle(color = ColorEraserCursor, radius = 22f, center = cursor)
        drawCircle(
            color = ColorEraserCursorBorder,
            radius = 22f,
            center = cursor,
            style = Stroke(width = 2f)
        )
    }
}
