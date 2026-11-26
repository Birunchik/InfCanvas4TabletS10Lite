package com.infinitecanvas.board.presentation.canvas.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.presentation.canvas.BoardImageCache
import com.infinitecanvas.board.presentation.canvas.CanvasGestureHandler
import kotlin.math.roundToInt

/**
 * Незакреплённые фотографии — часть «мира», рисуются под штрихами.
 * Координаты world-space; вызывать внутри withTransform.
 */
internal fun DrawScope.drawUnpinnedImages(
    images: List<BoardImage>,
    boardId: Long,
    imageCache: BoardImageCache,
    gestureHandler: CanvasGestureHandler,
    worldLeft: Double,
    worldTop: Double,
    worldRight: Double,
    worldBottom: Double
) {
    for (i in images.indices) {
        val img = images[i]
        if (img.isPinned) continue

        // Preview-bounds из gesture handler: модель обновляется только по отпускании,
        // а картинка должна ехать на каждом кадре драга/ресайза.
        val effectiveBounds = if (gestureHandler.previewImageId == img.id &&
            gestureHandler.previewImageBounds != null
        ) {
            gestureHandler.previewImageBounds!!
        } else {
            img.bounds
        }

        // Отсечение до getOrLoad: иначе фото за пределами экрана запускали бы decode из draw-фазы.
        if (effectiveBounds.maxX < worldLeft || effectiveBounds.minX > worldRight ||
            effectiveBounds.maxY < worldTop || effectiveBounds.minY > worldBottom
        ) {
            continue
        }

        val widthPx = (effectiveBounds.maxX - effectiveBounds.minX).toFloat().coerceAtLeast(1f)
        val heightPx = (effectiveBounds.maxY - effectiveBounds.minY).toFloat().coerceAtLeast(1f)

        val bitmap = imageCache.getOrLoad(boardId, img.fileName)
        if (bitmap != null) {
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(
                    effectiveBounds.minX.roundToInt(),
                    effectiveBounds.minY.roundToInt()
                ),
                dstSize = IntSize(widthPx.roundToInt(), heightPx.roundToInt())
            )
        } else {
            // getOrLoad из draw-фазы — побочный эффект, но decode идёт в IO; перерисовку разбудит onImageLoaded.
            drawRect(
                color = Color(0x1A1B6EF3),
                topLeft = Offset(effectiveBounds.minX.toFloat(), effectiveBounds.minY.toFloat()),
                size = Size(widthPx, heightPx)
            )
        }
    }
}

/**
 * Закреплённые (floating reference) фотографии — экранный слой поверх мира.
 * Координаты screen-space; вызывать вне withTransform.
 */
internal fun DrawScope.drawPinnedImages(
    images: List<BoardImage>,
    boardId: Long,
    imageCache: BoardImageCache,
    gestureHandler: CanvasGestureHandler,
    selectedImageId: Long?
) {
    for (i in images.indices) {
        val img = images[i]
        if (!img.isPinned) continue

        // Pinned-bounds уже в screen px и не проходят через worldToScreen: иначе фото уехало бы при зуме.
        val effectiveBounds = if (gestureHandler.previewImageId == img.id &&
            gestureHandler.previewImageBounds != null
        ) {
            gestureHandler.previewImageBounds!!
        } else {
            img.bounds
        }

        val widthPx = (effectiveBounds.maxX - effectiveBounds.minX).toFloat().coerceAtLeast(1f)
        val heightPx = (effectiveBounds.maxY - effectiveBounds.minY).toFloat().coerceAtLeast(1f)

        drawRect(
            color = Color(0x28000000),
            topLeft = Offset(
                effectiveBounds.minX.toFloat() + 2.dp.toPx(),
                effectiveBounds.minY.toFloat() + 4.dp.toPx()
            ),
            size = Size(widthPx, heightPx)
        )

        val bitmap = imageCache.getOrLoad(boardId, img.fileName)
        if (bitmap != null) {
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(
                    effectiveBounds.minX.roundToInt(),
                    effectiveBounds.minY.roundToInt()
                ),
                dstSize = IntSize(widthPx.roundToInt(), heightPx.roundToInt())
            )
        } else {
            drawRect(
                color = Color(0x1A1B6EF3),
                topLeft = Offset(effectiveBounds.minX.toFloat(), effectiveBounds.minY.toFloat()),
                size = Size(widthPx, heightPx)
            )
        }

        if (img.id != selectedImageId) {
            drawRect(
                color = Color(0xFF455A64),
                topLeft = Offset(effectiveBounds.minX.toFloat(), effectiveBounds.minY.toFloat()),
                size = Size(widthPx, heightPx),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
