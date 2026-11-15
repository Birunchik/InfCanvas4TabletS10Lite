package com.infinitecanvas.board.presentation.canvas

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
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

    var activeTick by remember { mutableIntStateOf(0) }

    val gestureHandler = remember {
        CanvasGestureHandler(
            viewportState = { currentViewport }, // <-- Передаем геттер
            activeTool = { currentTool },
            activeBrush = { currentBrush },
            selectedStrokeIds = { currentSelectedIds },
            selectionBounds = { currentSelectionBounds },
            quadtree = { currentQuadtree },
            isZoomLocked = isZoomLocked,
            allowFingerDrawing = allowFingerDrawing,
            onStrokeCommitted = currentOnCommit,
            onEraseStrokes = currentOnErase,
            onStrokesSelected = currentOnSelect,
            onMoveSelectedStrokes = currentOnMoveSelected,
            onTransform = currentOnTransform,
            onStateChanged = { activeTick++ }
        )
    }

    val pathCache = remember { StrokeGeometryCache() }
    val activePath = remember { Path() }
    val minorGridPath = remember { Path() }
    val majorGridPath = remember { Path() }
    val visibleStrokesBuffer = remember { ArrayList<Stroke>(128) }
    val draggedStrokesBuffer = remember { ArrayList<Stroke>(64) }

    val currentScaleFloat = viewportState.scale.toFloat().coerceAtLeast(0.1f)
    val worldDashEffect = remember(currentScaleFloat) {
        PathEffect.dashPathEffect(
            floatArrayOf(14f / currentScaleFloat, 10f / currentScaleFloat),
            0f
        )
    }
    val screenDashEffect = remember {
        PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInteropFilter { motionEvent ->
                gestureHandler.handlePointerInterop(motionEvent)
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    with(gestureHandler) {
                        observeStylusHover()
                    }
                }
            }
            .pointerInput(allowFingerDrawing, isZoomLocked) {
                awaitEachGesture {
                    with(gestureHandler) {
                        detectCanvasGestures(pathCache)
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

        val isDraggingNow = selectedStrokeIds.isNotEmpty() &&
                (gestureHandler.selectionDragDx != 0.0 || gestureHandler.selectionDragDy != 0.0)

        val queryBounds = WorldBounds(
            minX = worldLeft - abs(gestureHandler.selectionDragDx),
            minY = worldTop - abs(gestureHandler.selectionDragDy),
            maxX = worldRight + abs(gestureHandler.selectionDragDx),
            maxY = worldBottom + abs(gestureHandler.selectionDragDy)
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
            if (!isDraggingNow) {
                for (i in visibleStrokesBuffer.indices) {
                    val cachedGeometry = pathCache.getOrBuild(visibleStrokesBuffer[i])
                    drawCachedGeometry(cachedGeometry)
                }
            } else {
                draggedStrokesBuffer.clear()
                for (i in visibleStrokesBuffer.indices) {
                    val stroke = visibleStrokesBuffer[i]
                    if (stroke.id in selectedStrokeIds) {
                        draggedStrokesBuffer.add(stroke)
                    } else {
                        drawCachedGeometry(pathCache.getOrBuild(stroke))
                    }
                }
                if (draggedStrokesBuffer.isNotEmpty()) {
                    withTransform({
                        translate(gestureHandler.selectionDragDx.toFloat(), gestureHandler.selectionDragDy.toFloat())
                    }) {
                        for (i in draggedStrokesBuffer.indices) {
                            drawCachedGeometry(pathCache.getOrBuild(draggedStrokesBuffer[i]))
                        }
                    }
                }
            }

            if (gestureHandler.activePoints.isNotEmpty()) {
                val tempStroke = Stroke(
                    id = 0L,
                    points = gestureHandler.activePoints,
                    brush = currentBrush,
                    bounds = WorldBounds(0.0, 0.0, 0.0, 0.0)
                )
                val geom = buildGeometry(tempStroke, activePath)
                drawCachedGeometry(geom)
            }

            if (gestureHandler.lassoPoints.size > 1) {
                drawPath(
                    path = gestureHandler.lassoWorldPath,
                    color = Color(0x1A1B6EF3)
                )
                drawPath(
                    path = gestureHandler.lassoWorldPath,
                    color = Color(0xFF1B6EF3),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.5f / currentScaleFloat,
                        pathEffect = worldDashEffect
                    )
                )
            }
        }

        if (selectionBounds != null && selectedStrokeIds.isNotEmpty()) {
            val left = viewportState.worldToScreenX(selectionBounds.minX + gestureHandler.selectionDragDx)
            val top = viewportState.worldToScreenY(selectionBounds.minY + gestureHandler.selectionDragDy)
            val right = viewportState.worldToScreenX(selectionBounds.maxX + gestureHandler.selectionDragDx)
            val bottom = viewportState.worldToScreenY(selectionBounds.maxY + gestureHandler.selectionDragDy)

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
                    pathEffect = screenDashEffect
                )
            )
        }

        val cursor = gestureHandler.eraserScreenPos
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
            val b = s.bounds
            if (b.minX < minX) minX = b.minX
            if (b.minY < minY) minY = b.minY
            if (b.maxX > maxX) maxX = b.maxX
            if (b.maxY > maxY) maxY = b.maxY
            found = true
        }
    }
    if (!found) return null
    val padding = 8.0
    return WorldBounds(minX - padding, minY - padding, maxX + padding, maxY + padding)
}