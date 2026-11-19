package com.infinitecanvas.board.presentation.canvas

import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.domain.spatial.LassoSelector
import com.infinitecanvas.board.domain.spatial.Quadtree
import com.infinitecanvas.board.presentation.canvas.gestures.ImageGestureHandler
import com.infinitecanvas.board.presentation.canvas.gestures.StylusButtonManager
import com.infinitecanvas.board.presentation.canvas.gestures.appendFilteredPoints
import com.infinitecanvas.board.presentation.canvas.gestures.eraseAtScreenPosition
import com.infinitecanvas.board.presentation.canvas.gestures.handleViewportPanZoom
import com.infinitecanvas.board.presentation.canvas.renderer.CanvasRenderState
import kotlin.math.hypot

/**
 * Живёт в remember и не пересоздаётся между рекомпозициями, поэтому все зависимости —
 * лямбды-провайдеры: они читают актуальные значения, не перезапуская pointerInput.
 */
class CanvasGestureHandler(
    private val renderState: CanvasRenderState,
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
    private val stylusManager = StylusButtonManager(activeTool, onStateChanged)
    private val imageHandler = ImageGestureHandler(
        viewportState = viewportState,
        images = images,
        selectedImageId = selectedImageId,
        onImageSelected = onImageSelected,
        onUpdateImageBounds = onUpdateImageBounds,
        onStateChanged = onStateChanged
    )

    // State-списки, а не обычные: их мутирует жест, а Canvas читает размер в draw-фазе.
    val activePoints = mutableStateListOf<StrokePoint>()
    val lassoPoints = mutableStateListOf<StrokePoint>()
    var selectionDragDx by mutableDoubleStateOf(0.0)
    var selectionDragDy by mutableDoubleStateOf(0.0)

    val previewImageId: Long? get() = imageHandler.previewImageId
    val previewImageBounds: WorldBounds? get() = imageHandler.previewImageBounds
    val eraserScreenPos: Offset? get() = stylusManager.eraserScreenPos

    val lassoWorldPath = Path()

    fun handlePointerInterop(motionEvent: MotionEvent): Boolean =
        stylusManager.handlePointerInterop(motionEvent)

    suspend fun AwaitPointerEventScope.observeStylusHover() {
        with(stylusManager) { observeHover() }
    }

    suspend fun AwaitPointerEventScope.detectCanvasGestures(pathCache: StrokeGeometryCache) {
        // Приоритеты заданы порядком проверок: драг выделения → картинки → лассо → ластик/рисование.
        // Перестановка ломает взаимодействие: лассо перехватит драг выделения, а обработчик картинок — тап по фото.
        while (true) {
            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val isStylusOrEraser = firstDown.type == PointerType.Stylus || firstDown.type == PointerType.Eraser
            val isSingleFingerAllowed = allowFingerDrawing() && firstDown.type == PointerType.Touch

            val vp = viewportState()
            val worldDownX = vp.screenToWorldX(firstDown.position.x)
            val worldDownY = vp.screenToWorldY(firstDown.position.y)

            val imageHit = imageHandler.evaluateHit(firstDown.position, worldDownX, worldDownY, vp)

            // requireUnconsumed = false выше: down мог быть уже съеден interop-фильтром стилуса,
            // но рисование всё равно должно начаться. Тач без права рисования уходит в пан/зум, down не consume'им.
            if (!isStylusOrEraser && !isSingleFingerAllowed && !imageHit.allowsTouchInteraction) {
                handleViewportPanZoom(isZoomLocked, onTransform)
                continue
            }

            val pointerId = firstDown.id
            val initialEvent = currentEvent
            firstDown.consume()

            // Ластик включается тремя путями: тип пера, зажатая кнопка стилуса или вторичная кнопка.
            // Вторичная кнопка не считается ластиком, если зажата кнопка лассо.
            val isForcedEraser = firstDown.type == PointerType.Eraser ||
                    stylusManager.isHardwareEraserHeld ||
                    (initialEvent.buttons.isSecondaryPressed && !stylusManager.isHardwareLassoHeld)

            val isLassoMode = !isForcedEraser &&
                    (activeTool() == CanvasTool.LASSO || stylusManager.isHardwareLassoHeld)

            // 1. Приоритет: Перетаскивание выделения лассо
            val bounds = selectionBounds()
            val isDraggingSelection = !isForcedEraser &&
                    bounds != null &&
                    selectedStrokeIds().isNotEmpty() &&
                    worldDownX in bounds.minX..bounds.maxX &&
                    worldDownY in bounds.minY..bounds.maxY

            if (isDraggingSelection) {
                handleSelectionDrag(pointerId, worldDownX, worldDownY, pathCache)
                continue
            }

            // 2. Приоритет: Манипуляции с выделенной или закреплённой картинкой
            val handledImage = with(imageHandler) {
                tryHandleImmediateGesture(
                    firstDown = firstDown,
                    hit = imageHit,
                    isStylusOrEraser = isStylusOrEraser,
                    isForcedEraser = isForcedEraser,
                    isLassoMode = isLassoMode,
                    worldDownX = worldDownX,
                    worldDownY = worldDownY
                )
            }
            if (handledImage) continue

            // 3. Приоритет: Лассо
            if (isLassoMode) {
                handleLassoGesture(firstDown, pointerId)
                continue
            }

            if (selectedStrokeIds().isNotEmpty()) {
                onStrokesSelected(emptySet())
            }

            // 4. Приоритет: Ластик, рисование штриха или тап по обычной картинке
            val tappedUnselectedImage = if (!isStylusOrEraser && !isForcedEraser) imageHit.hitUnpinnedImg else null
            handleStrokeOrEraserGesture(
                firstDown = firstDown,
                pointerId = pointerId,
                isStylusOrEraser = isStylusOrEraser,
                isSingleFingerAllowed = isSingleFingerAllowed,
                isForcedEraser = isForcedEraser,
                tappedUnselectedImage = tappedUnselectedImage,
                pathCache = pathCache
            )
        }
    }

    private suspend fun AwaitPointerEventScope.handleSelectionDrag(
        pointerId: PointerId,
        startWorldX: Double,
        startWorldY: Double,
        pathCache: StrokeGeometryCache
    ) {
        // selectionDragDx/Dy — накопленное world-смещение за жест. В модель оно уходит только
        // на отпускании, а до тех пор Canvas рисует штрихи со сдвигом и расширяет query.
        var lastWorldX = startWorldX
        var lastWorldY = startWorldY
        selectionDragDx = 0.0
        selectionDragDy = 0.0
        onStateChanged()

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()

            if (change == null || !change.pressed) {
                change?.consume()
                // Смещение применяем к кэшу ровно один раз, на отпускании: во время драга
                // оно живёт только в draw-фазе, чтобы не трогать Path на каждом событии.
                if (selectionDragDx != 0.0 || selectionDragDy != 0.0) {
                    pathCache.shiftCachedStrokes(selectedStrokeIds(), selectionDragDx, selectionDragDy)
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
    }

    private suspend fun AwaitPointerEventScope.handleLassoGesture(
        firstDown: PointerInputChange,
        pointerId: PointerId
    ) {
        if (selectedStrokeIds().isNotEmpty()) onStrokesSelected(emptySet())
        if (selectedImageId() != null) onImageSelected(null)

        lassoPoints.clear()
        lassoWorldPath.reset()
        appendFilteredPoints(firstDown, viewportState(), lassoPoints, minStepPx = 6.0, incrementalPath = lassoWorldPath)
        onStateChanged()

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()

            if (change == null || !change.pressed) {
                if (change != null) {
                    appendFilteredPoints(change, viewportState(), lassoPoints, minStepPx = 6.0, incrementalPath = lassoWorldPath)
                    change.consume()
                }
                // Selection считаем один раз на отпускании: LassoSelector обходит quadtree,
                // и на каждом движении это было бы O(n) в кадре.
                val foundIds = LassoSelector.findSelectedStrokeIds(lassoPoints, quadtree())
                onStrokesSelected(foundIds)
                lassoPoints.clear()
                lassoWorldPath.reset()
                onStateChanged()
                break
            } else {
                if (appendFilteredPoints(change, viewportState(), lassoPoints, minStepPx = 6.0, incrementalPath = lassoWorldPath)) {
                    onStateChanged()
                }
                change.consume()
            }
        }
    }

    private suspend fun AwaitPointerEventScope.handleStrokeOrEraserGesture(
        firstDown: PointerInputChange,
        pointerId: PointerId,
        isStylusOrEraser: Boolean,
        isSingleFingerAllowed: Boolean,
        isForcedEraser: Boolean,
        tappedUnselectedImage: BoardImage?,
        pathCache: StrokeGeometryCache
    ) {
        // Один цикл на рисование и стирание: переключение кнопкой пера на лету не должно
        // обрывать жест, поэтому isErasingNow — var, а не отдельная ветка сценария.
        var isErasingNow = isStylusOrEraser && (isForcedEraser || activeTool() == CanvasTool.ERASER)

        if (isErasingNow) {
            stylusManager.updateEraserPos(firstDown.position)
            eraseAtScreenPosition(
                screenPos = firstDown.position,
                viewport = viewportState(),
                quadtree = quadtree(),
                candidatesBuffer = renderState.eraserCandidatesBuffer,
                hitIdsBuffer = renderState.eraserHitIdsBuffer,
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
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()

            val shouldEraseOnThisEvent = isStylusOrEraser && (
                    activeTool() == CanvasTool.ERASER ||
                            change?.type == PointerType.Eraser ||
                            stylusManager.isHardwareEraserHeld ||
                            (event.buttons.isSecondaryPressed && !stylusManager.isHardwareLassoHeld)
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
                    val committed = Stroke.create(System.nanoTime(), activePoints.toList(), activeBrush())
                    // Прогреваем кэш до коммита: иначе первый кадр после коммита строил бы Path
                    // в draw-фазе — заметный фриз на длинном штрихе.
                    pathCache.getOrBuild(committed)
                    onStrokeCommitted(committed)
                }
                activePoints.clear()
                stylusManager.clearEraserPos()
                onStateChanged()
                break
            } else {
                // Порог 8 px отделяет тап от пана: без него микро-дрожание пальца превращало бы тап в панорамирование.
                if (!movedBeyondTapThreshold && hypot(change.position.x - downScreenPos.x, change.position.y - downScreenPos.y) > 8f) {
                    movedBeyondTapThreshold = true
                }

                if (!isStylusOrEraser && !isSingleFingerAllowed && movedBeyondTapThreshold) {
                    val rawZoom = event.calculateZoom()
                    val pan = if (event.changes.count { it.pressed } > 1) event.calculatePan() else change.position - lastPanPos
                    val centroid = event.calculateCentroid(useCurrent = false).let { if (it == Offset.Unspecified) change.position else it }
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
                            val committed = Stroke.create(System.nanoTime(), activePoints.toList(), activeBrush())
                            pathCache.getOrBuild(committed)
                            onStrokeCommitted(committed)
                        }
                        activePoints.clear()
                        isErasingNow = true
                    } else if (!shouldEraseOnThisEvent && isErasingNow) {
                        stylusManager.clearEraserPos()
                        activePoints.clear()
                        isErasingNow = false
                    }

                    if (isErasingNow) {
                        stylusManager.updateEraserPos(change.position)
                        eraseAtScreenPosition(
                            screenPos = change.position,
                            viewport = viewportState(),
                            quadtree = quadtree(),
                            candidatesBuffer = renderState.eraserCandidatesBuffer,
                            hitIdsBuffer = renderState.eraserHitIdsBuffer,
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