package com.infinitecanvas.board.presentation.canvas.gestures

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerId
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.spatial.LassoSelector
import com.infinitecanvas.board.domain.spatial.Quadtree
import com.infinitecanvas.board.presentation.canvas.CanvasInteractionState

internal class LassoGestureHandler(
    private val viewportState: () -> ViewportState,
    private val quadtree: () -> Quadtree,
    private val selectedStrokeIds: () -> Set<Long>,
    private val selectedImageId: () -> Long?,
    private val interactionState: CanvasInteractionState,
    private val onStrokesSelected: (Set<Long>) -> Unit,
    private val onImageSelected: (Long?) -> Unit,
    private val onStateChanged: () -> Unit
) {
    val lassoPoints = interactionState.lassoPoints
    val lassoWorldPath: Path = interactionState.lassoWorldPath

    suspend fun AwaitPointerEventScope.handleLassoGesture(
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
}
