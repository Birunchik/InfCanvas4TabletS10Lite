package com.infinitecanvas.board.presentation.canvas

import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.domain.spatial.Quadtree
import com.infinitecanvas.board.presentation.canvas.gestures.ImageGestureHandler
import com.infinitecanvas.board.presentation.canvas.gestures.LassoGestureHandler
import com.infinitecanvas.board.presentation.canvas.gestures.SelectionGestureHandler
import com.infinitecanvas.board.presentation.canvas.gestures.StrokeOrEraserGestureHandler
import com.infinitecanvas.board.presentation.canvas.gestures.StylusButtonManager
import com.infinitecanvas.board.presentation.canvas.gestures.ViewportGestureHandler
import com.infinitecanvas.board.presentation.canvas.renderer.CanvasRenderState

/**
 * Живёт в remember и не пересоздаётся между рекомпозициями, поэтому все зависимости —
 * лямбды-провайдеры: они читают актуальные значения, не перезапуская pointerInput.
 */
internal class CanvasGestureHandler(
    private val renderState: CanvasRenderState,
    private val interactionState: CanvasInteractionState,
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
    private val selectionHandler = SelectionGestureHandler(
        viewportState = viewportState,
        selectedStrokeIds = selectedStrokeIds,
        interactionState = interactionState,
        onMoveSelectedStrokes = onMoveSelectedStrokes,
        onStateChanged = onStateChanged
    )
    private val lassoHandler = LassoGestureHandler(
        viewportState = viewportState,
        quadtree = quadtree,
        selectedStrokeIds = selectedStrokeIds,
        selectedImageId = selectedImageId,
        interactionState = interactionState,
        onStrokesSelected = onStrokesSelected,
        onImageSelected = onImageSelected,
        onStateChanged = onStateChanged
    )
    private val strokeOrEraserHandler = StrokeOrEraserGestureHandler(
        viewportState = viewportState,
        activeBrush = activeBrush,
        activeTool = activeTool,
        selectedImageId = selectedImageId,
        quadtree = quadtree,
        renderState = renderState,
        stylusManager = stylusManager,
        interactionState = interactionState,
        isZoomLocked = isZoomLocked,
        onStrokeCommitted = onStrokeCommitted,
        onEraseStrokes = onEraseStrokes,
        onImageSelected = onImageSelected,
        onTransform = onTransform,
        onStateChanged = onStateChanged
    )
    private val viewportHandler = ViewportGestureHandler(
        isZoomLocked = isZoomLocked,
        onTransform = onTransform
    )

    // State-списки, а не обычные: их мутирует жест, а Canvas читает размер в draw-фазе.
    val activePoints: List<StrokePoint> get() = interactionState.activePoints
    val lassoPoints: List<StrokePoint> get() = interactionState.lassoPoints
    var selectionDragDx: Double
        get() = selectionHandler.selectionDragDx
        set(_) {}
    var selectionDragDy: Double
        get() = selectionHandler.selectionDragDy
        set(_) {}

    val previewImageId: Long? get() = imageHandler.previewImageId
    val previewImageBounds: WorldBounds? get() = imageHandler.previewImageBounds
    val eraserScreenPos: Offset? get() = stylusManager.eraserScreenPos

    val lassoWorldPath: Path get() = interactionState.lassoWorldPath

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
                with(viewportHandler) { handleViewportPanZoom() }
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
                with(selectionHandler) {
                    handleSelectionDrag(pointerId, worldDownX, worldDownY, pathCache)
                }
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
                with(lassoHandler) {
                    handleLassoGesture(firstDown, pointerId)
                }
                continue
            }

            if (selectedStrokeIds().isNotEmpty()) {
                onStrokesSelected(emptySet())
            }

            // 4. Приоритет: Ластик, рисование штриха или тап по обычной картинке
            val tappedUnselectedImage = if (!isStylusOrEraser && !isForcedEraser) imageHit.hitUnpinnedImg else null
            with(strokeOrEraserHandler) {
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
    }
}
