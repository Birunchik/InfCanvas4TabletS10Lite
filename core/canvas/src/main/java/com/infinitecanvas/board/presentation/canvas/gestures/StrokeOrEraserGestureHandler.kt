package com.infinitecanvas.board.presentation.canvas.gestures

import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
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
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.spatial.Quadtree
import com.infinitecanvas.board.presentation.canvas.CanvasInteractionState
import com.infinitecanvas.board.presentation.canvas.StrokeGeometryCache
import com.infinitecanvas.board.presentation.canvas.renderer.CanvasRenderState
import kotlin.math.hypot

internal class StrokeOrEraserGestureHandler(
    private val viewportState: () -> ViewportState,
    private val activeBrush: () -> Brush,
    private val activeTool: () -> CanvasTool,
    private val selectedImageId: () -> Long?,
    private val quadtree: () -> Quadtree,
    private val renderState: CanvasRenderState,
    private val stylusManager: StylusButtonManager,
    private val interactionState: CanvasInteractionState,
    private val isZoomLocked: () -> Boolean,
    private val onStrokeCommitted: (Stroke) -> Unit,
    private val onEraseStrokes: (Set<Long>) -> Unit,
    private val onImageSelected: (Long?) -> Unit,
    private val onTransform: (panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) -> Unit,
    private val onStateChanged: () -> Unit
) {
    val activePoints = interactionState.activePoints

    suspend fun AwaitPointerEventScope.handleStrokeOrEraserGesture(
        firstDown: PointerInputChange,
        pointerId: PointerId,
        isStylusOrEraser: Boolean,
        isSingleFingerAllowed: Boolean,
        isForcedEraser: Boolean,
        tappedUnselectedImage: BoardImage?,
        pathCache: StrokeGeometryCache
    ) {
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
                    pathCache.getOrBuild(committed)
                    onStrokeCommitted(committed)
                }
                activePoints.clear()
                stylusManager.clearEraserPos()
                onStateChanged()
                break
            } else {
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
