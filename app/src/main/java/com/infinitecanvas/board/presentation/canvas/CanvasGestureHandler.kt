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

/**
 * Класс, инкапсулирующий всю логику обработки жестов (pointerInput, pointerInteropFilter)
 * для бесконечного холста.
 */
class CanvasGestureHandler(
    private val viewportState: () -> ViewportState,
    private val activeTool: () -> CanvasTool,
    private val activeBrush: () -> Brush,
    private val selectedStrokeIds: () -> Set<Long>,
    private val selectionBounds: () -> WorldBounds?,
    private val quadtree: () -> Quadtree,
    private val isZoomLocked: Boolean,
    private val allowFingerDrawing: Boolean,
    private val onStrokeCommitted: (Stroke) -> Unit,
    private val onEraseStrokes: (Set<Long>) -> Unit,
    private val onStrokesSelected: (Set<Long>) -> Unit,
    private val onMoveSelectedStrokes: (dx: Double, dy: Double) -> Unit,
    private val onTransform: (panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) -> Unit,
    private val onStateChanged: () -> Unit
) {
    val activePoints = mutableStateListOf<StrokePoint>()
    val lassoPoints = mutableStateListOf<StrokePoint>()
    var selectionDragDx by mutableDoubleStateOf(0.0)
    var selectionDragDy by mutableDoubleStateOf(0.0)

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
            val isSingleFingerAllowed = allowFingerDrawing &&
                    firstDown.type == PointerType.Touch

            if (!isStylusOrEraser && !isSingleFingerAllowed) {
                do {
                    val event = awaitPointerEvent()
                    val stylusAppeared = event.changes.any {
                        it.type == PointerType.Stylus || it.type == PointerType.Eraser
                    }
                    if (stylusAppeared) break

                    val rawZoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val zoom = if (isZoomLocked) 1f else rawZoom

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

            val worldDownX = viewportState().screenToWorldX(firstDown.position.x)
            val worldDownY = viewportState().screenToWorldY(firstDown.position.y)

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

            if (isLassoMode) {
                if (selectedStrokeIds().isNotEmpty()) {
                    onStrokesSelected(emptySet())
                }
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

            var isErasingNow = isForcedEraser || activeTool() == CanvasTool.ERASER

            if (isErasingNow) {
                eraserScreenPos = firstDown.position
                eraseAtScreenPosition(
                    screenPos = firstDown.position,
                    viewport = viewportState(),
                    quadtree = quadtree(),
                    onErase = onEraseStrokes
                )
            } else {
                activePoints.clear()
                appendFilteredPoints(firstDown, viewportState(), activePoints)
            }
            onStateChanged()

            while (true) {
                val event: PointerEvent = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                    ?: event.changes.firstOrNull()

                val shouldEraseOnThisEvent = activeTool() == CanvasTool.ERASER ||
                        change?.type == PointerType.Eraser ||
                        isHardwareEraserHeld ||
                        (event.buttons.isSecondaryPressed && !isHardwareLassoHeld)

                if (change == null || !change.pressed) {
                    if (!isErasingNow && change != null) {
                        appendFilteredPoints(change, viewportState(), activePoints)
                    }
                    change?.consume()

                    if (!isErasingNow && activePoints.isNotEmpty()) {
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

                    change.consume()
                    onStateChanged()
                }
            }
        }
    }
}