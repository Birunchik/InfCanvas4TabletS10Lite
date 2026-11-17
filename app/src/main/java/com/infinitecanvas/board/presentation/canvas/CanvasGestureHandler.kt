package com.infinitecanvas.board.presentation.canvas

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.*
import com.infinitecanvas.board.domain.model.*
import com.infinitecanvas.board.domain.spatial.LassoSelector
import com.infinitecanvas.board.domain.spatial.Quadtree
import kotlin.math.hypot
import kotlin.math.max

private enum class ImageResizeCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

private fun BoardImage.toScreenBounds(vp: ViewportState): WorldBounds =
    if (isPinned) {
        bounds
    } else {
        WorldBounds(
            minX = vp.worldToScreenX(bounds.minX).toDouble(),
            minY = vp.worldToScreenY(bounds.minY).toDouble(),
            maxX = vp.worldToScreenX(bounds.maxX).toDouble(),
            maxY = vp.worldToScreenY(bounds.maxY).toDouble()
        )
    }

class CanvasGestureHandler(
    private val viewportState: () -> ViewportState,
    private val activeTool: () -> CanvasTool,
    private val activeBrush: () -> Brush,
    private val selectedStrokeIds: () -> Set<Long>,
    private val selectionBounds: () -> WorldBounds?,
    private val images: () -> List<BoardImage>,
    private val selectedImageId: () -> Long?,
    private val quadtree: () -> Quadtree,
    private val isZoomLocked: () -> Boolean,
    private val allowFingerDrawing: () -> Boolean,
    private val onStrokeCommitted: (Stroke) -> Unit,
    private val onEraseStrokes: (Set<Long>) -> Unit,
    private val onStrokesSelected: (Set<Long>) -> Unit,
    private val onMoveSelectedStrokes: (dx: Double, dy: Double) -> Unit,
    private val onImageSelected: (Long?) -> Unit,
    private val onUpdateImageBounds: (imageId: Long, newBounds: WorldBounds) -> Unit,
    private val onTransform: (panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) -> Unit,
    private val onStateChanged: () -> Unit
) {
    val activePoints = mutableStateListOf<StrokePoint>()
    val lassoPoints = mutableStateListOf<StrokePoint>()
    var selectionDragDx by mutableDoubleStateOf(0.0)
    var selectionDragDy by mutableDoubleStateOf(0.0)

    // Временные границы картинки во время перетаскивания/масштабирования (для плавных 90 Гц)
    var previewImageId by mutableStateOf<Long?>(null)
    var previewImageBounds by mutableStateOf<WorldBounds?>(null)

    var eraserScreenPos by mutableStateOf<Offset?>(null)

    var isHardwareEraserHeld by mutableStateOf(false)
    var isHardwareLassoHeld by mutableStateOf(false)
    var wasStylusButtonPressed by mutableStateOf(false)
    var lastButtonPressTimeMs by mutableLongStateOf(0L)

    val lassoWorldPath = Path()

    fun handlePointerInterop(motionEvent: MotionEvent): Boolean {
        val state = motionEvent.buttonState
        val pressed = (state and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                (state and MotionEvent.BUTTON_SECONDARY) != 0

        if (pressed && !wasStylusButtonPressed) {
            val now = SystemClock.uptimeMillis()
            if (now - lastButtonPressTimeMs <= 400L) {
                isHardwareLassoHeld = true
                isHardwareEraserHeld = false
            } else {
                isHardwareEraserHeld = true
                isHardwareLassoHeld = false
            }
            lastButtonPressTimeMs = now
        } else if (!pressed && wasStylusButtonPressed) {
            isHardwareEraserHeld = false
            isHardwareLassoHeld = false
        }
        wasStylusButtonPressed = pressed
        return false
    }

    suspend fun AwaitPointerEventScope.observeStylusHover() {
        while (true) {
            val event = awaitPointerEvent()
            val stylusChange = event.changes.firstOrNull {
                it.type == PointerType.Stylus || it.type == PointerType.Eraser
            }

            if (stylusChange == null || event.type == PointerEventType.Exit) {
                if (eraserScreenPos != null) {
                    eraserScreenPos = null
                    onStateChanged()
                }
            } else if (!stylusChange.pressed) {
                val showPreview = !isHardwareLassoHeld && (
                        isHardwareEraserHeld ||
                                event.buttons.isSecondaryPressed ||
                                stylusChange.type == PointerType.Eraser ||
                                activeTool() == CanvasTool.ERASER
                        )

                if (showPreview) {
                    eraserScreenPos = stylusChange.position
                    onStateChanged()
                } else if (eraserScreenPos != null) {
                    eraserScreenPos = null
                    onStateChanged()
                }
            }
        }
    }

    suspend fun AwaitPointerEventScope.detectCanvasGestures(pathCache: StrokeGeometryCache) {
        while (true) {
            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val isStylusOrEraser = firstDown.type == PointerType.Stylus ||
                    firstDown.type == PointerType.Eraser
            val isSingleFingerAllowed = allowFingerDrawing() &&
                    firstDown.type == PointerType.Touch

            val vp = viewportState()
            val screenDownX = firstDown.position.x.toDouble()
            val screenDownY = firstDown.position.y.toDouble()
            val worldDownX = vp.screenToWorldX(firstDown.position.x)
            val worldDownY = vp.screenToWorldY(firstDown.position.y)

            val allImages = images()
            val currentSelImgId = selectedImageId()
            val selectedImg = if (currentSelImgId != null) {
                allImages.firstOrNull { it.id == currentSelImgId }
            } else {
                null
            }

            val hitCornerOnSelected = if (selectedImg != null) {
                detectResizeCorner(firstDown.position, selectedImg.toScreenBounds(vp))
            } else {
                null
            }
            val hitSelectedImgBody = if (selectedImg != null) {
                val sb = selectedImg.toScreenBounds(vp)
                screenDownX in sb.minX..sb.maxX && screenDownY in sb.minY..sb.maxY
            } else {
                false
            }

            val hitPinnedImg = allImages.lastOrNull { img ->
                img.isPinned &&
                        screenDownX in img.bounds.minX..img.bounds.maxX &&
                        screenDownY in img.bounds.minY..img.bounds.maxY
            }

            val hitUnpinnedImg = allImages.lastOrNull { img ->
                !img.isPinned &&
                        worldDownX in img.bounds.minX..img.bounds.maxX &&
                        worldDownY in img.bounds.minY..img.bounds.maxY
            }

            // Палец может перехватывать жест, если касается углов/тела выделенной картинки,
            // любой картинки для выделения/перетаскивания, или если картинка уже выделена (для тапа мимо неё)
            val allowTouchForImageInteraction = !isStylusOrEraser && (
                    hitCornerOnSelected != null ||
                            hitSelectedImgBody ||
                            hitPinnedImg != null ||
                            hitUnpinnedImg != null ||
                            selectedImg != null
                    )

            if (!isStylusOrEraser && !isSingleFingerAllowed && !allowTouchForImageInteraction) {
                do {
                    val event = awaitPointerEvent()
                    val stylusAppeared = event.changes.any {
                        it.type == PointerType.Stylus || it.type == PointerType.Eraser
                    }
                    if (stylusAppeared) break

                    val rawZoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val zoom = if (isZoomLocked()) 1f else rawZoom

                    if (centroid != Offset.Unspecified && (zoom != 1f || pan != Offset.Zero)) {
                        onTransform(pan.x, pan.y, zoom, centroid.x, centroid.y)
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                continue
            }

            val pointerId = firstDown.id
            val initialEvent = currentEvent
            firstDown.consume()

            val isForcedEraser = firstDown.type == PointerType.Eraser ||
                    isHardwareEraserHeld ||
                    (initialEvent.buttons.isSecondaryPressed && !isHardwareLassoHeld)

            val isLassoMode = !isForcedEraser &&
                    (activeTool() == CanvasTool.LASSO || isHardwareLassoHeld)

            // 1. Приоритет: Перетаскивание выделения лассо
            val bounds = selectionBounds()
            val isDraggingSelection = !isForcedEraser &&
                    bounds != null &&
                    selectedStrokeIds().isNotEmpty() &&
                    worldDownX in bounds.minX..bounds.maxX &&
                    worldDownY in bounds.minY..bounds.maxY

            if (isDraggingSelection) {
                var lastWorldX = worldDownX
                var lastWorldY = worldDownY
                selectionDragDx = 0.0
                selectionDragDy = 0.0
                onStateChanged()

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull()

                    if (change == null || !change.pressed) {
                        change?.consume()
                        if (selectionDragDx != 0.0 || selectionDragDy != 0.0) {
                            pathCache.shiftCachedStrokes(
                                selectedStrokeIds(),
                                selectionDragDx,
                                selectionDragDy
                            )
                            onMoveSelectedStrokes(selectionDragDx, selectionDragDy)
                        }
                        selectionDragDx = 0.0
                        selectionDragDy = 0.0
                        onStateChanged()
                        break
                    } else {
                        val currWX = viewportState().screenToWorldX(change.position.x)
                        val currWY = viewportState().screenToWorldY(change.position.y)
                        selectionDragDx += (currWX - lastWorldX)
                        selectionDragDy += (currWY - lastWorldY)
                        lastWorldX = currWX
                        lastWorldY = currWY
                        change.consume()
                        onStateChanged()
                    }
                }
                continue
            }

            // 2. Приоритет: Работа с уже выделенной картинкой (управление только пальцем; тап мимо снимает выделение)
            if (selectedImg != null) {
                if (!isStylusOrEraser && !isForcedEraser && !isLassoMode && hitCornerOnSelected != null) {
                    handleImageResize(pointerId, selectedImg, hitCornerOnSelected)
                    continue
                } else if (!isStylusOrEraser && !isForcedEraser && !isLassoMode && hitSelectedImgBody) {
                    val startX = if (selectedImg.isPinned) screenDownX else worldDownX
                    val startY = if (selectedImg.isPinned) screenDownY else worldDownY
                    handleImageDrag(pointerId, selectedImg, startX, startY)
                    continue
                } else if (isStylusOrEraser || (!hitSelectedImgBody && hitCornerOnSelected == null)) {
                    // Снимаем выделение при клике/касании мимо картинки (или при начале работы стилусом)
                    onImageSelected(null)
                }
            }

            // 2.5. Приоритет: Невыделенная закреплённая картинка (выделение и сдвиг только пальцем)
            if (!isStylusOrEraser && !isForcedEraser && !isLassoMode && hitPinnedImg != null) {
                onImageSelected(hitPinnedImg.id)
                val corner = detectResizeCorner(firstDown.position, hitPinnedImg.bounds)
                if (corner != null) {
                    handleImageResize(pointerId, hitPinnedImg, corner)
                } else {
                    handleImageDrag(pointerId, hitPinnedImg, screenDownX, screenDownY)
                }
                continue
            }

            // 3. Приоритет: Лассо
            if (isLassoMode) {
                if (selectedStrokeIds().isNotEmpty()) onStrokesSelected(emptySet())
                if (selectedImageId() != null) onImageSelected(null)

                lassoPoints.clear()
                lassoWorldPath.reset()
                appendFilteredPoints(
                    change = firstDown,
                    viewport = viewportState(),
                    target = lassoPoints,
                    minStepPx = 6.0,
                    incrementalPath = lassoWorldPath
                )
                onStateChanged()

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull()

                    if (change == null || !change.pressed) {
                        if (change != null) {
                            appendFilteredPoints(
                                change = change,
                                viewport = viewportState(),
                                target = lassoPoints,
                                minStepPx = 6.0,
                                incrementalPath = lassoWorldPath
                            )
                            change.consume()
                        }
                        val foundIds = LassoSelector.findSelectedStrokeIds(
                            lassoPolygon = lassoPoints,
                            quadtree = quadtree()
                        )
                        onStrokesSelected(foundIds)
                        lassoPoints.clear()
                        lassoWorldPath.reset()
                        onStateChanged()
                        break
                    } else {
                        if (appendFilteredPoints(
                                change = change,
                                viewport = viewportState(),
                                target = lassoPoints,
                                minStepPx = 6.0,
                                incrementalPath = lassoWorldPath
                            )
                        ) {
                            onStateChanged()
                        }
                        change.consume()
                    }
                }
                continue
            }

            if (selectedStrokeIds().isNotEmpty()) {
                onStrokesSelected(emptySet())
            }

            // 4. Приоритет: Ластик, рисование штриха стилусом или тап пальцем по обычной картинке
            var isErasingNow = isStylusOrEraser && (isForcedEraser || activeTool() == CanvasTool.ERASER)
            val tappedUnselectedImage = if (!isStylusOrEraser && !isErasingNow) hitUnpinnedImg else null

            if (isErasingNow) {
                eraserScreenPos = firstDown.position
                eraseAtScreenPosition(
                    screenPos = firstDown.position,
                    viewport = viewportState(),
                    quadtree = quadtree(),
                    onErase = onEraseStrokes
                )
            } else if (isStylusOrEraser || isSingleFingerAllowed) {
                activePoints.clear()
                appendFilteredPoints(firstDown, viewportState(), activePoints)
            }
            onStateChanged()

            var movedBeyondTapThreshold = false
            val downScreenPos = firstDown.position
            var lastPanPos = firstDown.position

            while (true) {
                val event: PointerEvent = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                    ?: event.changes.firstOrNull()

                val shouldEraseOnThisEvent = isStylusOrEraser && (
                        activeTool() == CanvasTool.ERASER ||
                                change?.type == PointerType.Eraser ||
                                isHardwareEraserHeld ||
                                (event.buttons.isSecondaryPressed && !isHardwareLassoHeld)
                        )

                if (change == null || !change.pressed) {
                    if ((isStylusOrEraser || isSingleFingerAllowed) && !isErasingNow && change != null) {
                        appendFilteredPoints(change, viewportState(), activePoints)
                    }
                    change?.consume()

                    if (!isStylusOrEraser && !movedBeyondTapThreshold) {
                        if (tappedUnselectedImage != null) {
                            onImageSelected(tappedUnselectedImage.id)
                        } else if (selectedImageId() != null) {
                            onImageSelected(null)
                        }
                        activePoints.clear()
                        onStateChanged()
                        break
                    }

                    if ((isStylusOrEraser || isSingleFingerAllowed) && !isErasingNow && activePoints.isNotEmpty()) {
                        val committed = Stroke.create(
                            id = System.nanoTime(),
                            points = activePoints.toList(),
                            brush = activeBrush()
                        )
                        pathCache.getOrBuild(committed)
                        onStrokeCommitted(committed)
                    }
                    activePoints.clear()
                    eraserScreenPos = null
                    onStateChanged()
                    break
                } else {
                    if (!movedBeyondTapThreshold) {
                        val dist = hypot(
                            change.position.x - downScreenPos.x,
                            change.position.y - downScreenPos.y
                        )
                        if (dist > 8f) movedBeyondTapThreshold = true
                    }

                    // Если палец начал двигаться поверх невыделенной картинки (а не тапнул), двигаем/зумим холст
                    if (!isStylusOrEraser && !isSingleFingerAllowed && movedBeyondTapThreshold) {
                        val rawZoom = event.calculateZoom()
                        val pan = if (event.changes.count { it.pressed } > 1) {
                            event.calculatePan()
                        } else {
                            change.position - lastPanPos
                        }
                        val centroid = event.calculateCentroid(useCurrent = false).let {
                            if (it == Offset.Unspecified) change.position else it
                        }
                        val zoom = if (isZoomLocked()) 1f else rawZoom
                        onTransform(pan.x, pan.y, zoom, centroid.x, centroid.y)
                        lastPanPos = change.position
                        event.changes.forEach { it.consume() }
                        continue
                    }
                    lastPanPos = change.position

                    if (isStylusOrEraser || isSingleFingerAllowed) {
                        if (shouldEraseOnThisEvent && !isErasingNow) {
                            if (activePoints.size > 1) {
                                val committed = Stroke.create(
                                    id = System.nanoTime(),
                                    points = activePoints.toList(),
                                    brush = activeBrush()
                                )
                                pathCache.getOrBuild(committed)
                                onStrokeCommitted(committed)
                            }
                            activePoints.clear()
                            isErasingNow = true
                        } else if (!shouldEraseOnThisEvent && isErasingNow) {
                            eraserScreenPos = null
                            activePoints.clear()
                            isErasingNow = false
                        }

                        if (isErasingNow) {
                            eraserScreenPos = change.position
                            eraseAtScreenPosition(
                                screenPos = change.position,
                                viewport = viewportState(),
                                quadtree = quadtree(),
                                onErase = onEraseStrokes
                            )
                        } else {
                            appendFilteredPoints(change, viewportState(), activePoints)
                        }
                    }

                    change.consume()
                    onStateChanged()
                }
            }
        }
    }

    private fun detectResizeCorner(
        screenPos: Offset,
        screenBounds: WorldBounds
    ): ImageResizeCorner? {
        val hitRadiusPx = 28f
        val corners = listOf(
            ImageResizeCorner.TOP_LEFT to Offset(screenBounds.minX.toFloat(), screenBounds.minY.toFloat()),
            ImageResizeCorner.TOP_RIGHT to Offset(screenBounds.maxX.toFloat(), screenBounds.minY.toFloat()),
            ImageResizeCorner.BOTTOM_LEFT to Offset(screenBounds.minX.toFloat(), screenBounds.maxY.toFloat()),
            ImageResizeCorner.BOTTOM_RIGHT to Offset(screenBounds.maxX.toFloat(), screenBounds.maxY.toFloat())
        )
        return corners.firstOrNull { (_, pt) ->
            hypot(screenPos.x - pt.x, screenPos.y - pt.y) <= hitRadiusPx
        }?.first
    }

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
                val curX = if (image.isPinned) {
                    change.position.x.toDouble()
                } else {
                    viewportState().screenToWorldX(change.position.x)
                }
                val curY = if (image.isPinned) {
                    change.position.y.toDouble()
                } else {
                    viewportState().screenToWorldY(change.position.y)
                }
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

    private suspend fun AwaitPointerEventScope.handleImageResize(
        pointerId: PointerId,
        image: BoardImage,
        corner: ImageResizeCorner
    ) {
        val orig = image.bounds
        val aspectRatio = ((orig.maxX - orig.minX) / (orig.maxY - orig.minY).coerceAtLeast(1.0))
            .coerceAtLeast(0.1)
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
                val curX = if (image.isPinned) {
                    change.position.x.toDouble()
                } else {
                    viewportState().screenToWorldX(change.position.x)
                }
                val curY = if (image.isPinned) {
                    change.position.y.toDouble()
                } else {
                    viewportState().screenToWorldY(change.position.y)
                }

                currentBounds = when (corner) {
                    ImageResizeCorner.BOTTOM_RIGHT -> {
                        val newW = max(minSize, curX - orig.minX)
                        val newH = newW / aspectRatio
                        WorldBounds(orig.minX, orig.minY, orig.minX + newW, orig.minY + newH)
                    }
                    ImageResizeCorner.BOTTOM_LEFT -> {
                        val newW = max(minSize, orig.maxX - curX)
                        val newH = newW / aspectRatio
                        WorldBounds(orig.maxX - newW, orig.minY, orig.maxX, orig.minY + newH)
                    }
                    ImageResizeCorner.TOP_RIGHT -> {
                        val newW = max(minSize, curX - orig.minX)
                        val newH = newW / aspectRatio
                        WorldBounds(orig.minX, orig.maxY - newH, orig.minX + newW, orig.maxY)
                    }
                    ImageResizeCorner.TOP_LEFT -> {
                        val newW = max(minSize, orig.maxX - curX)
                        val newH = newW / aspectRatio
                        WorldBounds(orig.maxX - newW, orig.maxY - newH, orig.maxX, orig.maxY)
                    }
                }
                previewImageBounds = currentBounds
                change.consume()
                onStateChanged()
            }
        }
    }
}