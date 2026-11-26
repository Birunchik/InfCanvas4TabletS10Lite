package com.infinitecanvas.board.presentation.canvas.gestures

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.presentation.canvas.CanvasInteractionState
import com.infinitecanvas.board.presentation.canvas.StrokeGeometryCache

internal class SelectionGestureHandler(
    private val viewportState: () -> ViewportState,
    private val selectedStrokeIds: () -> Set<Long>,
    private val interactionState: CanvasInteractionState,
    private val onMoveSelectedStrokes: (dx: Double, dy: Double) -> Unit,
    private val onStateChanged: () -> Unit
) {
    val selectionDragDx: Double get() = interactionState.selectionDragDx
    val selectionDragDy: Double get() = interactionState.selectionDragDy

    suspend fun AwaitPointerEventScope.handleSelectionDrag(
        pointerId: PointerId,
        startWorldX: Double,
        startWorldY: Double,
        pathCache: StrokeGeometryCache
    ) {
        var lastWorldX = startWorldX
        var lastWorldY = startWorldY
        interactionState.selectionDragDx = 0.0
        interactionState.selectionDragDy = 0.0
        onStateChanged()

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()

            if (change == null || !change.pressed) {
                change?.consume()
                if (interactionState.selectionDragDx != 0.0 || interactionState.selectionDragDy != 0.0) {
                    pathCache.shiftCachedStrokes(selectedStrokeIds(), interactionState.selectionDragDx, interactionState.selectionDragDy)
                    onMoveSelectedStrokes(interactionState.selectionDragDx, interactionState.selectionDragDy)
                }
                interactionState.selectionDragDx = 0.0
                interactionState.selectionDragDy = 0.0
                onStateChanged()
                break
            } else {
                val currWX = viewportState().screenToWorldX(change.position.x)
                val currWY = viewportState().screenToWorldY(change.position.y)
                interactionState.selectionDragDx += (currWX - lastWorldX)
                interactionState.selectionDragDy += (currWY - lastWorldY)
                lastWorldX = currWX
                lastWorldY = currWY
                change.consume()
                onStateChanged()
            }
        }
    }
}
