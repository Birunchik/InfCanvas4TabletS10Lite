package com.infinitecanvas.board.presentation.canvas.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.domain.spatial.Quadtree
import com.infinitecanvas.board.presentation.canvas.CanvasGestureHandler
import com.infinitecanvas.board.presentation.canvas.StrokeGeometryCache
import com.infinitecanvas.board.presentation.canvas.renderer.CanvasRenderState

@Composable
fun rememberCanvasGestureHandler(
    viewportState: ViewportState,
    activeTool: CanvasTool,
    activeBrush: Brush,
    selectedStrokeIds: Set<Long>,
    selectionBounds: WorldBounds?,
    images: List<BoardImage>,
    selectedImageId: Long?,
    quadtree: Quadtree,
    isZoomLocked: Boolean,
    allowFingerDrawing: Boolean,
    renderState: CanvasRenderState,
    onStrokeCommitted: (Stroke) -> Unit,
    onEraseStrokes: (Set<Long>) -> Unit,
    onStrokesSelected: (Set<Long>) -> Unit,
    onMoveSelectedStrokes: (dx: Double, dy: Double) -> Unit,
    onImageSelected: (Long?) -> Unit,
    onUpdateImageBounds: (Long, WorldBounds) -> Unit,
    onTransform: (panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) -> Unit
): CanvasGestureHandler {
    // Handler создаётся один раз (remember без ключей), поэтому все изменяемые входы
    // читаются через rememberUpdatedState: замыкания видят актуальные состояние и колбэки.
    val currentViewport by rememberUpdatedState(viewportState)
    val currentTool by rememberUpdatedState(activeTool)
    val currentBrush by rememberUpdatedState(activeBrush)
    val currentSelectedIds by rememberUpdatedState(selectedStrokeIds)
    val currentSelectionBounds by rememberUpdatedState(selectionBounds)
    val currentImages by rememberUpdatedState(images)
    val currentSelectedImageId by rememberUpdatedState(selectedImageId)
    val currentQuadtree by rememberUpdatedState(quadtree)
    val currentIsZoomLocked by rememberUpdatedState(isZoomLocked)
    val currentAllowFinger by rememberUpdatedState(allowFingerDrawing)

    val currentOnCommit by rememberUpdatedState(onStrokeCommitted)
    // Обёртка чистит pathCache до вызова ViewModel: удалённый штрих не должен остаться
    // в кеше геометрии, иначе он «мигнёт» при следующей перерисовке.
    val currentOnErase by rememberUpdatedState<(Set<Long>) -> Unit> { erasedIds ->
        renderState.pathCache.removeStrokes(erasedIds)
        onEraseStrokes(erasedIds)
    }
    val currentOnSelect by rememberUpdatedState(onStrokesSelected)
    val currentOnMoveSelected by rememberUpdatedState(onMoveSelectedStrokes)
    val currentOnImageSelected by rememberUpdatedState(onImageSelected)
    val currentOnUpdateImageBounds by rememberUpdatedState(onUpdateImageBounds)
    val currentOnTransform by rememberUpdatedState(onTransform)

    return remember {
        CanvasGestureHandler(
            renderState = renderState,
            viewportState = { currentViewport },
            activeTool = { currentTool },
            activeBrush = { currentBrush },
            selectedStrokeIds = { currentSelectedIds },
            selectionBounds = { currentSelectionBounds },
            images = { currentImages },
            selectedImageId = { currentSelectedImageId },
            quadtree = { currentQuadtree },
            isZoomLocked = { currentIsZoomLocked },
            allowFingerDrawing = { currentAllowFinger },
            onStrokeCommitted = currentOnCommit,
            onEraseStrokes = currentOnErase,
            onStrokesSelected = currentOnSelect,
            onMoveSelectedStrokes = currentOnMoveSelected,
            onImageSelected = currentOnImageSelected,
            onUpdateImageBounds = currentOnUpdateImageBounds,
            onTransform = currentOnTransform,
            onStateChanged = { renderState.invalidate() }
        )
    }
}

/**
 * Устанавливает pointer-handler'ы для Canvas.
 *
 * Порядок модификаторов важен:
 *  1) pointerInteropFilter — MotionEvent для стилуса/пера (interop);
 *  2) pointerInput — stylus hover (awaitPointerEventScope);
 *  3) pointerInput — основной жест-детектор (awaitEachGesture).
 *
 * Сам `detectCanvasGestures` внутри дергает `handleViewportPanZoom` и др. из `CanvasGestures.kt`.
 */
fun Modifier.canvasGestures(
    gestureHandler: CanvasGestureHandler,
    pathCache: StrokeGeometryCache
): Modifier = this
    .pointerInteropFilter { motionEvent ->
        gestureHandler.handlePointerInterop(motionEvent)
    }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            with(gestureHandler) { observeStylusHover() }
        }
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            // pointerInput(Unit): перезапуск на каждый кадр убил бы текущий жест. Актуальность
            // данных обеспечивает rememberUpdatedState выше, а не ключи модификатора.
            with(gestureHandler) { detectCanvasGestures(pathCache) }
        }
    }