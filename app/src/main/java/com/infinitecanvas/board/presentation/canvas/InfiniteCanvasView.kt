package com.infinitecanvas.board.presentation.canvas

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.*
import com.infinitecanvas.board.domain.model.*
import com.infinitecanvas.board.domain.spatial.LassoSelector
import com.infinitecanvas.board.domain.spatial.Quadtree
import kotlin.math.abs

@Composable
fun InfiniteCanvasView(
    viewportState: ViewportState,
    strokes: List<Stroke>,
    activeTool: CanvasTool,
    activeBrush: Brush,
    selectedStrokeIds: Set<Long>,
    isZoomLocked: Boolean = false,
    allowFingerDrawing: Boolean = false,
    onStrokeCommitted: (Stroke) -> Unit,
    onEraseStrokes: (Set<Long>) -> Unit,
    onStrokesSelected: (Set<Long>) -> Unit,
    onMoveSelectedStrokes: (dx: Double, dy: Double) -> Unit,
    onTransform: (panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFF8F9FA),
    gridColor: Color = Color(0xFFE0E3E7),
    majorGridColor: Color = Color(0xFFC4C9D0)
) {
    val currentViewport by rememberUpdatedState(viewportState)
    val currentTool by rememberUpdatedState(activeTool)
    val currentBrush by rememberUpdatedState(activeBrush)
    val currentSelectedIds by rememberUpdatedState(selectedStrokeIds)
    val currentOnCommit by rememberUpdatedState(onStrokeCommitted)
    val currentOnErase by rememberUpdatedState(onEraseStrokes)
    val currentOnSelect by rememberUpdatedState(onStrokesSelected)
    val currentOnMoveSelected by rememberUpdatedState(onMoveSelectedStrokes)
    val currentOnTransform by rememberUpdatedState(onTransform)

    val quadtree = remember(strokes) { Quadtree.build(strokes) }
    val currentQuadtree by rememberUpdatedState(quadtree)

    val selectionBounds = remember(strokes, selectedStrokeIds) {
        if (selectedStrokeIds.isEmpty()) null
        else computeSelectionWorldBounds(strokes, selectedStrokeIds)
    }
    val currentSelectionBounds by rememberUpdatedState(selectionBounds)

    val activePoints = remember { mutableStateListOf<StrokePoint>() }
    val lassoPoints = remember { mutableStateListOf<StrokePoint>() }
    var selectionDragDx by remember { mutableDoubleStateOf(0.0) }
    var selectionDragDy by remember { mutableDoubleStateOf(0.0) }

    var eraserScreenPos by remember { mutableStateOf<Offset?>(null) }
    var activeTick by remember { mutableIntStateOf(0) }

    var isHardwareEraserHeld by remember { mutableStateOf(false) }
    var isHardwareLassoHeld by remember { mutableStateOf(false) }
    var wasStylusButtonPressed by remember { mutableStateOf(false) }
    var lastButtonPressTimeMs by remember { mutableLongStateOf(0L) }

    val pathCache = remember { StrokeGeometryCache() }
    val activePath = remember { Path() }
    val lassoPath = remember { Path() }
    val minorGridPath = remember { Path() }
    val majorGridPath = remember { Path() }
    val visibleStrokesBuffer = remember { ArrayList<Stroke>(128) }
    val lassoDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInteropFilter { motionEvent ->
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
                false
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val stylusChange = event.changes.firstOrNull {
                            it.type == PointerType.Stylus || it.type == PointerType.Eraser
                        }

                        if (stylusChange == null || event.type == PointerEventType.Exit) {
                            if (eraserScreenPos != null) {
                                eraserScreenPos = null
                                activeTick++
                            }
                        } else if (!stylusChange.pressed) {
                            val showPreview = !isHardwareLassoHeld && (
                                    isHardwareEraserHeld ||
                                            (event.buttons.isSecondaryPressed && !isHardwareLassoHeld) ||
                                            stylusChange.type == PointerType.Eraser ||
                                            currentTool == CanvasTool.ERASER
                                    )

                            if (showPreview) {
                                eraserScreenPos = stylusChange.position
                                activeTick++
                            } else if (eraserScreenPos != null) {
                                eraserScreenPos = null
                                activeTick++
                            }
                        }
                    }
                }
            }
            .pointerInput(allowFingerDrawing, isZoomLocked) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val isStylusOrEraser = firstDown.type == PointerType.Stylus ||
                            firstDown.type == PointerType.Eraser
                    val isSingleFingerAllowed = allowFingerDrawing &&
                            firstDown.type == PointerType.Touch

                    if (isStylusOrEraser || isSingleFingerAllowed) {
                        val pointerId = firstDown.id
                        val initialEvent = currentEvent
                        firstDown.consume()

                        val isLassoMode = currentTool == CanvasTool.LASSO || isHardwareLassoHeld
                        val worldDownX = currentViewport.screenToWorldX(firstDown.position.x)
                        val worldDownY = currentViewport.screenToWorldY(firstDown.position.y)

                        val bounds = currentSelectionBounds
                        val isDraggingSelection = bounds != null &&
                                currentSelectedIds.isNotEmpty() &&
                                worldDownX in bounds.minX..bounds.maxX &&
                                worldDownY in bounds.minY..bounds.maxY

                        if (isDraggingSelection) {
                            var lastWorldX = worldDownX
                            var lastWorldY = worldDownY
                            selectionDragDx = 0.0
                            selectionDragDy = 0.0
                            activeTick++

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()

                                if (change == null || !change.pressed) {
                                    change?.consume()
                                    if (selectionDragDx != 0.0 || selectionDragDy != 0.0) {
                                        currentOnMoveSelected(selectionDragDx, selectionDragDy)
                                    }
                                    selectionDragDx = 0.0
                                    selectionDragDy = 0.0
                                    activeTick++
                                    break
                                } else {
                                    val currWX = currentViewport.screenToWorldX(change.position.x)
                                    val currWY = currentViewport.screenToWorldY(change.position.y)
                                    selectionDragDx += (currWX - lastWorldX)
                                    selectionDragDy += (currWY - lastWorldY)
                                    lastWorldX = currWX
                                    lastWorldY = currWY
                                    change.consume()
                                    activeTick++
                                }
                            }
                            return@awaitEachGesture
                        }

                        if (isLassoMode) {
                            if (currentSelectedIds.isNotEmpty()) {
                                currentOnSelect(emptySet())
                            }
                            lassoPoints.clear()
                            appendFilteredPoints(firstDown, currentViewport, lassoPoints)
                            activeTick++

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()

                                if (change == null || !change.pressed) {
                                    if (change != null) {
                                        appendFilteredPoints(change, currentViewport, lassoPoints)
                                        change.consume()
                                    }
                                    val foundIds = LassoSelector.findSelectedStrokeIds(
                                        lassoPolygon = lassoPoints,
                                        quadtree = currentQuadtree
                                    )
                                    currentOnSelect(foundIds)
                                    lassoPoints.clear()
                                    activeTick++
                                    break
                                } else {
                                    appendFilteredPoints(change, currentViewport, lassoPoints)
                                    change.consume()
                                    activeTick++
                                }
                            }
                            return@awaitEachGesture
                        }

                        if (currentSelectedIds.isNotEmpty()) {
                            currentOnSelect(emptySet())
                        }

                        var isErasingNow = currentTool == CanvasTool.ERASER ||
                                firstDown.type == PointerType.Eraser ||
                                isHardwareEraserHeld ||
                                initialEvent.buttons.isSecondaryPressed

                        if (isErasingNow) {
                            eraserScreenPos = firstDown.position
                            eraseAtScreenPosition(
                                screenPos = firstDown.position,
                                viewport = currentViewport,
                                quadtree = currentQuadtree,
                                onErase = currentOnErase
                            )
                        } else {
                            activePoints.clear()
                            appendFilteredPoints(firstDown, currentViewport, activePoints)
                        }
                        activeTick++

                        while (true) {
                            val event: PointerEvent = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()

                            val shouldEraseOnThisEvent = currentTool == CanvasTool.ERASER ||
                                    change?.type == PointerType.Eraser ||
                                    isHardwareEraserHeld ||
                                    event.buttons.isSecondaryPressed

                            if (change == null || !change.pressed) {
                                if (!isErasingNow && change != null) {
                                    appendFilteredPoints(change, currentViewport, activePoints)
                                }
                                change?.consume()

                                if (!isErasingNow && activePoints.isNotEmpty()) {
                                    val committed = Stroke.create(
                                        id = System.nanoTime(),
                                        points = activePoints.toList(),
                                        brush = currentBrush
                                    )
                                    pathCache.getOrBuild(committed)
                                    currentOnCommit(committed)
                                }
                                activePoints.clear()
                                eraserScreenPos = null
                                activeTick++
                                break
                            } else {
                                if (shouldEraseOnThisEvent && !isErasingNow) {
                                    if (activePoints.size > 1) {
                                        val committed = Stroke.create(
                                            id = System.nanoTime(),
                                            points = activePoints.toList(),
                                            brush = currentBrush
                                        )
                                        pathCache.getOrBuild(committed)
                                        currentOnCommit(committed)
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
                                        viewport = currentViewport,
                                        quadtree = currentQuadtree,
                                        onErase = currentOnErase
                                    )
                                } else {
                                    appendFilteredPoints(change, currentViewport, activePoints)
                                }

                                change.consume()
                                activeTick++
                            }
                        }
                    } else {
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
                                currentOnTransform(pan.x, pan.y, zoom, centroid.x, centroid.y)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        activeTick

        val canvasWidth = size.width
        val canvasHeight = size.height

        val worldLeft = viewportState.screenToWorldX(0f)
        val worldTop = viewportState.screenToWorldY(0f)
        val worldRight = viewportState.screenToWorldX(canvasWidth)
        val worldBottom = viewportState.screenToWorldY(canvasHeight)

        drawBatchedWorldGrid(
            viewportState = viewportState,
            worldLeft = worldLeft,
            worldTop = worldTop,
            worldRight = worldRight,
            worldBottom = worldBottom,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            gridColor = gridColor,
            majorGridColor = majorGridColor,
            minorGridPath = minorGridPath,
            majorGridPath = majorGridPath
        )

        val queryBounds = WorldBounds(
            minX = worldLeft - abs(selectionDragDx),
            minY = worldTop - abs(selectionDragDy),
            maxX = worldRight + abs(selectionDragDx),
            maxY = worldBottom + abs(selectionDragDy)
        )
        quadtree.query(queryBounds, visibleStrokesBuffer)

        withTransform({
            translate(
                left = viewportState.offsetX.toFloat(),
                top = viewportState.offsetY.toFloat()
            )
            scale(
                scaleX = viewportState.scale.toFloat(),
                scaleY = viewportState.scale.toFloat(),
                pivot = Offset.Zero
            )
        }) {
            for (i in visibleStrokesBuffer.indices) {
                val stroke = visibleStrokesBuffer[i]
                val cachedGeometry = pathCache.getOrBuild(stroke)
                if (stroke.id in selectedStrokeIds && (selectionDragDx != 0.0 || selectionDragDy != 0.0)) {
                    withTransform({
                        translate(selectionDragDx.toFloat(), selectionDragDy.toFloat())
                    }) {
                        drawCachedGeometry(cachedGeometry)
                    }
                } else {
                    drawCachedGeometry(cachedGeometry)
                }
            }

            if (activePoints.isNotEmpty()) {
                val tempStroke = Stroke(id = 0L, points = activePoints, brush = currentBrush, bounds = WorldBounds(0.0, 0.0, 0.0, 0.0))
                val geom = buildGeometry(tempStroke, activePath)
                drawCachedGeometry(geom)
            }
        }

        if (lassoPoints.size > 1) {
            lassoPath.reset()
            val p0 = lassoPoints[0]
            lassoPath.moveTo(viewportState.worldToScreenX(p0.x), viewportState.worldToScreenY(p0.y))
            for (i in 1 until lassoPoints.size) {
                val p = lassoPoints[i]
                lassoPath.lineTo(viewportState.worldToScreenX(p.x), viewportState.worldToScreenY(p.y))
            }
            lassoPath.close()

            drawPath(path = lassoPath, color = Color(0x1A1B6EF3))
            drawPath(
                path = lassoPath,
                color = Color(0xFF1B6EF3),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.5f,
                    pathEffect = lassoDashEffect
                )
            )
        }

        if (selectionBounds != null && selectedStrokeIds.isNotEmpty()) {
            val left = viewportState.worldToScreenX(selectionBounds.minX + selectionDragDx)
            val top = viewportState.worldToScreenY(selectionBounds.minY + selectionDragDy)
            val right = viewportState.worldToScreenX(selectionBounds.maxX + selectionDragDx)
            val bottom = viewportState.worldToScreenY(selectionBounds.maxY + selectionDragDy)

            drawRect(
                color = Color(0x141B6EF3),
                topLeft = Offset(left, top),
                size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f))
            )
            drawRect(
                color = Color(0xFF276DE8),
                topLeft = Offset(left, top),
                size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2f,
                    pathEffect = lassoDashEffect
                )
            )
        }

        val cursor = eraserScreenPos
        if (cursor != null) {
            drawCircle(color = Color(0x33838383), radius = 22f, center = cursor)
            drawCircle(
                color = Color(0xFF6E6E6E),
                radius = 22f,
                center = cursor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
    }
}

private fun computeSelectionWorldBounds(
    strokes: List<Stroke>,
    selectedIds: Set<Long>
): WorldBounds? {
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    var found = false

    for (i in strokes.indices) {
        val s = strokes[i]
        if (s.id in selectedIds) {
            for (j in s.points.indices) {
                val p = s.points[j]
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
                found = true
            }
        }
    }
    if (!found) return null
    val padding = 12.0
    return WorldBounds(minX - padding, minY - padding, maxX + padding, maxY + padding)
}