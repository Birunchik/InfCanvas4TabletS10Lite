package com.infinitecanvas.board.presentation.canvas.gestures

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds
import kotlin.math.hypot
import kotlin.math.max

internal enum class ImageResizeCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

internal class ImageHitResult(
    val selectedImg: BoardImage?,
    val hitCornerOnSelected: ImageResizeCorner?,
    val hitSelectedImgBody: Boolean,
    val hitPinnedImg: BoardImage?,
    val hitUnpinnedImg: BoardImage?
) {
    val allowsTouchInteraction: Boolean
        get() = hitCornerOnSelected != null ||
                hitSelectedImgBody ||
                hitPinnedImg != null ||
                hitUnpinnedImg != null ||
                selectedImg != null
}

// previewImageBounds — кадровое состояние drag/resize: в ViewModel bounds уходят
// только по отпусканию, чтобы не гонять autosave на каждый кадр.
internal class ImageGestureHandler(
    private val viewportState: () -> ViewportState,
    private val images: () -> List<BoardImage>,
    private val selectedImageId: () -> Long?,
    private val onImageSelected: (Long?) -> Unit,
    private val onUpdateImageBounds: (imageId: Long, newBounds: WorldBounds) -> Unit,
    private val onStateChanged: () -> Unit
) {
    var previewImageId by mutableStateOf<Long?>(null)
        private set
    var previewImageBounds by mutableStateOf<WorldBounds?>(null)
        private set

    // Приоритет: угол выделенной → её тело → pinned → unpinned. Pinned проверяется
    // в screen-координатах, unpinned — в world; vp даёт обе системы.
    fun evaluateHit(
        screenPos: Offset,
        worldX: Double,
        worldY: Double,
        vp: ViewportState
    ): ImageHitResult {
        val screenX = screenPos.x.toDouble()
        val screenY = screenPos.y.toDouble()
        val allImages = images()
        val currentSelId = selectedImageId()
        val selectedImg = if (currentSelId != null) allImages.firstOrNull { it.id == currentSelId } else null

        val hitCorner = selectedImg?.let { detectResizeCorner(screenPos, it.toScreenBounds(vp)) }
        val hitSelectedBody = selectedImg?.let {
            val sb = it.toScreenBounds(vp)
            screenX in sb.minX..sb.maxX && screenY in sb.minY..sb.maxY
        } ?: false

        // lastOrNull, а не firstOrNull: список идёт в порядке отрисовки, и касание должно
        // доставаться верхней картинке, а не первой по id.
        val hitPinned = allImages.lastOrNull { img ->
            img.isPinned && screenX in img.bounds.minX..img.bounds.maxX && screenY in img.bounds.minY..img.bounds.maxY
        }

        val hitUnpinned = allImages.lastOrNull { img ->
            !img.isPinned && worldX in img.bounds.minX..img.bounds.maxX && worldY in img.bounds.minY..img.bounds.maxY
        }

        return ImageHitResult(
            selectedImg = selectedImg,
            hitCornerOnSelected = hitCorner,
            hitSelectedImgBody = hitSelectedBody,
            hitPinnedImg = hitPinned,
            hitUnpinnedImg = hitUnpinned
        )
    }

    /**
     * Пытается перехватить жест для взаимодействия с выделенной или закреплённой картинкой.
     * Возвращает true, если жест полностью обработан.
     *
     * Стилус и forced eraser картинку не перетаскивают: ими рисуют/стирают, поэтому
     * для них выделение только сбрасывается. Ресайз — лишь для touch.
     */
    suspend fun AwaitPointerEventScope.tryHandleImmediateGesture(
        firstDown: PointerInputChange,
        hit: ImageHitResult,
        isStylusOrEraser: Boolean,
        isForcedEraser: Boolean,
        isLassoMode: Boolean,
        worldDownX: Double,
        worldDownY: Double
    ): Boolean {
        val pointerId = firstDown.id
        val screenDownX = firstDown.position.x.toDouble()
        val screenDownY = firstDown.position.y.toDouble()

        val selectedImg = hit.selectedImg
        if (selectedImg != null) {
            // Угол важнее тела: иначе попадание в угол внутри bounds начинало бы drag,
            // и ресайз был бы недоступен у маленьких картинок.
            if (!isStylusOrEraser && !isForcedEraser && !isLassoMode && hit.hitCornerOnSelected != null) {
                handleImageResize(pointerId, selectedImg, hit.hitCornerOnSelected)
                return true
            } else if (!isStylusOrEraser && !isForcedEraser && !isLassoMode && hit.hitSelectedImgBody) {
                val startX = if (selectedImg.isPinned) screenDownX else worldDownX
                val startY = if (selectedImg.isPinned) screenDownY else worldDownY
                handleImageDrag(pointerId, selectedImg, startX, startY)
                return true
            } else if (isStylusOrEraser || (!hit.hitSelectedImgBody && hit.hitCornerOnSelected == null)) {
                onImageSelected(null)
            }
        }

        // Невыделенную закреплённую картинку при первом касании выделяем и перетаскиваем,
        // без случайного срабатывания ресайза за невидимые угловые маркеры
        val hitPinnedImg = hit.hitPinnedImg
        if (!isStylusOrEraser && !isForcedEraser && !isLassoMode && hitPinnedImg != null) {
            onImageSelected(hitPinnedImg.id)
            handleImageDrag(pointerId, hitPinnedImg, screenDownX, screenDownY)
            return true
        }

        return false
    }
    // Порог 28 px — в экранных px, не в world: угловые маркеры рисуются фиксированного
    // размера, поэтому хит-зона не должна расти вместе с зумом.
    private fun detectResizeCorner(screenPos: Offset, screenBounds: WorldBounds): ImageResizeCorner? {
        val hitRadiusPx = 28f
        val px = screenPos.x
        val py = screenPos.y
        val minX = screenBounds.minX.toFloat()
        val minY = screenBounds.minY.toFloat()
        val maxX = screenBounds.maxX.toFloat()
        val maxY = screenBounds.maxY.toFloat()

        return when {
            hypot(px - minX, py - minY) <= hitRadiusPx -> ImageResizeCorner.TOP_LEFT
            hypot(px - maxX, py - minY) <= hitRadiusPx -> ImageResizeCorner.TOP_RIGHT
            hypot(px - minX, py - maxY) <= hitRadiusPx -> ImageResizeCorner.BOTTOM_LEFT
            hypot(px - maxX, py - maxY) <= hitRadiusPx -> ImageResizeCorner.BOTTOM_RIGHT
            else -> null
        }
    }
    // Для pinned дельта считается в screen, для остальных — в world: bounds pinned
    // уже в screen-координатах, смешивать системы нельзя.
    private suspend fun AwaitPointerEventScope.handleImageDrag(
        pointerId: PointerId,
        image: BoardImage,
        startX: Double,
        startY: Double
    ) {
        var lastX = startX
        var lastY = startY
        var currentBounds = image.bounds
        previewImageId = image.id
        previewImageBounds = currentBounds
        onStateChanged()

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
            if (change == null || !change.pressed) {
                change?.consume()
                if (currentBounds != image.bounds) {
                    onUpdateImageBounds(image.id, currentBounds)
                }
                previewImageId = null
                previewImageBounds = null
                onStateChanged()
                break
            } else {
                val curX = if (image.isPinned) change.position.x.toDouble() else viewportState().screenToWorldX(change.position.x)
                val curY = if (image.isPinned) change.position.y.toDouble() else viewportState().screenToWorldY(change.position.y)
                val dx = curX - lastX
                val dy = curY - lastY
                lastX = curX
                lastY = curY
                currentBounds = WorldBounds(
                    minX = currentBounds.minX + dx,
                    minY = currentBounds.minY + dy,
                    maxX = currentBounds.maxX + dx,
                    maxY = currentBounds.maxY + dy
                )
                previewImageBounds = currentBounds
                change.consume()
                onStateChanged()
            }
        }
    }

    // aspectRatio фиксируется на старте: ширина ведёт, высота выводится из неё,
    // иначе при смене угла картинка меняла бы пропорции.
    private suspend fun AwaitPointerEventScope.handleImageResize(
        pointerId: PointerId,
        image: BoardImage,
        corner: ImageResizeCorner
    ) {
        val orig = image.bounds
        val aspectRatio = ((orig.maxX - orig.minX) / (orig.maxY - orig.minY).coerceAtLeast(1.0)).coerceAtLeast(0.1)
        // Минимумы в разных системах: 80 screen px для pinned и 48 world для остальных —
        // ниже этого углы налезают друг на друга, и попадание становится неоднозначным.
        val minSize = if (image.isPinned) 80.0 else 48.0

        var currentBounds = orig
        previewImageId = image.id
        previewImageBounds = currentBounds
        onStateChanged()

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
            if (change == null || !change.pressed) {
                change?.consume()
                if (currentBounds != orig) {
                    onUpdateImageBounds(image.id, currentBounds)
                }
                previewImageId = null
                previewImageBounds = null
                onStateChanged()
                break
            } else {
                val curX = if (image.isPinned) change.position.x.toDouble() else viewportState().screenToWorldX(change.position.x)
                val curY = if (image.isPinned) change.position.y.toDouble() else viewportState().screenToWorldY(change.position.y)

                currentBounds = when (corner) {
                    ImageResizeCorner.BOTTOM_RIGHT -> {
                        val newW = max(minSize, curX - orig.minX)
                        WorldBounds(orig.minX, orig.minY, orig.minX + newW, orig.minY + newW / aspectRatio)
                    }
                    ImageResizeCorner.BOTTOM_LEFT -> {
                        val newW = max(minSize, orig.maxX - curX)
                        WorldBounds(orig.maxX - newW, orig.minY, orig.maxX, orig.minY + newW / aspectRatio)
                    }
                    ImageResizeCorner.TOP_RIGHT -> {
                        val newW = max(minSize, curX - orig.minX)
                        WorldBounds(orig.minX, orig.maxY - newW / aspectRatio, orig.minX + newW, orig.maxY)
                    }
                    ImageResizeCorner.TOP_LEFT -> {
                        val newW = max(minSize, orig.maxX - curX)
                        WorldBounds(orig.maxX - newW, orig.maxY - newW / aspectRatio, orig.maxX, orig.maxY)
                    }
                }
                previewImageBounds = currentBounds
                change.consume()
                onStateChanged()
            }
        }
    }

    private fun BoardImage.toScreenBounds(vp: ViewportState): WorldBounds =
        if (isPinned) bounds else WorldBounds(
            minX = vp.worldToScreenX(bounds.minX).toDouble(),
            minY = vp.worldToScreenY(bounds.minY).toDouble(),
            maxX = vp.worldToScreenX(bounds.maxX).toDouble(),
            maxY = vp.worldToScreenY(bounds.maxY).toDouble()
        )
}