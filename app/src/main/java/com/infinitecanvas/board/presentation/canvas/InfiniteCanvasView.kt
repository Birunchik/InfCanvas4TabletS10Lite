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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.domain.model.*
import com.infinitecanvas.board.domain.spatial.Quadtree
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InfiniteCanvasView(
    boardId: Long,
    viewportState: ViewportState,
    strokes: List<Stroke>,
    images: List<BoardImage>,
    selectedImageId: Long?,
    imageCache: BoardImageCache,
    imageLoadTick: Int = 0,
    activeTool: CanvasTool,
    activeBrush: Brush,
    selectedStrokeIds: Set<Long>,
    isZoomLocked: Boolean = false,
    allowFingerDrawing: Boolean = false,
    onCanvasViewportSizeChanged: (widthPx: Int, heightPx: Int) -> Unit,
    onStrokeCommitted: (Stroke) -> Unit,
    onEraseStrokes: (Set<Long>) -> Unit,
    onStrokesSelected: (Set<Long>) -> Unit,
    onMoveSelectedStrokes: (dx: Double, dy: Double) -> Unit,
    onImageSelected: (Long?) -> Unit,
    onUpdateImageBounds: (imageId: Long, newBounds: WorldBounds) -> Unit,
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
    val currentImages by rememberUpdatedState(images)
    val currentSelectedImageId by rememberUpdatedState(selectedImageId)
    val currentIsZoomLocked by rememberUpdatedState(isZoomLocked)
    val currentAllowFinger by rememberUpdatedState(allowFingerDrawing)

    val currentOnCommit by rememberUpdatedState(onStrokeCommitted)
    val currentOnErase by rememberUpdatedState(onEraseStrokes)
    val currentOnSelect by rememberUpdatedState(onStrokesSelected)
    val currentOnMoveSelected by rememberUpdatedState(onMoveSelectedStrokes)
    val currentOnImageSelected by rememberUpdatedState(onImageSelected)
    val currentOnUpdateImageBounds by rememberUpdatedState(onUpdateImageBounds)
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
            onStateChanged = { activeTick++ }
        )
    }

    val pathCache = remember { StrokeGeometryCache() }
    var cachedActivePointCount by remember { mutableIntStateOf(-1) }
    var cachedActiveGeometry by remember { mutableStateOf<CachedStrokeGeometry?>(null) }

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
            .onSizeChanged { size ->
                onCanvasViewportSizeChanged(size.width, size.height)
            }
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
            .pointerInput(Unit) {
                awaitEachGesture {
                    with(gestureHandler) {
                        detectCanvasGestures(pathCache)
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        activeTick
        @Suppress("UNUSED_EXPRESSION")
        imageLoadTick

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
            // Слой 1: Незакреплённые фотографии на холсте (под штрихами)
            for (i in images.indices) {
                val img = images[i]
                if (img.isPinned) continue

                val effectiveBounds = if (gestureHandler.previewImageId == img.id && gestureHandler.previewImageBounds != null) {
                    gestureHandler.previewImageBounds!!
                } else {
                    img.bounds
                }

                if (effectiveBounds.maxX < worldLeft || effectiveBounds.minX > worldRight ||
                    effectiveBounds.maxY < worldTop || effectiveBounds.minY > worldBottom
                ) {
                    continue
                }

                val bitmap = imageCache.getOrLoad(boardId, img.fileName)
                if (bitmap != null) {
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset(
                            effectiveBounds.minX.roundToInt(),
                            effectiveBounds.minY.roundToInt()
                        ),
                        dstSize = IntSize(
                            (effectiveBounds.maxX - effectiveBounds.minX).roundToInt().coerceAtLeast(1),
                            (effectiveBounds.maxY - effectiveBounds.minY).roundToInt().coerceAtLeast(1)
                        )
                    )
                } else {
                    drawRect(
                        color = Color(0x1A1B6EF3),
                        topLeft = Offset(effectiveBounds.minX.toFloat(), effectiveBounds.minY.toFloat()),
                        size = Size(
                            (effectiveBounds.maxX - effectiveBounds.minX).toFloat(),
                            (effectiveBounds.maxY - effectiveBounds.minY).toFloat()
                        )
                    )
                }
            }

            // Слой 2а: Зафиксированные штрихи ВЫДЕЛИТЕЛЯ (всегда ниже ручки и пера)
            if (!isDraggingNow) {
                for (i in visibleStrokesBuffer.indices) {
                    val stroke = visibleStrokesBuffer[i]
                    if (stroke.brush.type == BrushType.HIGHLIGHTER) {
                        drawCachedGeometry(pathCache.getOrBuild(stroke))
                    }
                }
                // Слой 2б: Зафиксированные штрихи РУЧКИ и ПЕРА (поверх выделителя)
                for (i in visibleStrokesBuffer.indices) {
                    val stroke = visibleStrokesBuffer[i]
                    if (stroke.brush.type != BrushType.HIGHLIGHTER) {
                        drawCachedGeometry(pathCache.getOrBuild(stroke))
                    }
                }
            } else {
                draggedStrokesBuffer.clear()
                for (i in visibleStrokesBuffer.indices) {
                    val stroke = visibleStrokesBuffer[i]
                    if (stroke.id in selectedStrokeIds) {
                        draggedStrokesBuffer.add(stroke)
                    } else if (stroke.brush.type == BrushType.HIGHLIGHTER) {
                        drawCachedGeometry(pathCache.getOrBuild(stroke))
                    }
                }
                for (i in visibleStrokesBuffer.indices) {
                    val stroke = visibleStrokesBuffer[i]
                    if (stroke.id !in selectedStrokeIds && stroke.brush.type != BrushType.HIGHLIGHTER) {
                        drawCachedGeometry(pathCache.getOrBuild(stroke))
                    }
                }
                if (draggedStrokesBuffer.isNotEmpty()) {
                    withTransform({
                        translate(gestureHandler.selectionDragDx.toFloat(), gestureHandler.selectionDragDy.toFloat())
                    }) {
                        for (i in draggedStrokesBuffer.indices) {
                            val stroke = draggedStrokesBuffer[i]
                            if (stroke.brush.type == BrushType.HIGHLIGHTER) {
                                drawCachedGeometry(pathCache.getOrBuild(stroke))
                            }
                        }
                        for (i in draggedStrokesBuffer.indices) {
                            val stroke = draggedStrokesBuffer[i]
                            if (stroke.brush.type != BrushType.HIGHLIGHTER) {
                                drawCachedGeometry(pathCache.getOrBuild(stroke))
                            }
                        }
                    }
                }
            }

            // Слой 3: Активный рисуемый штрих
            val activeCount = gestureHandler.activePoints.size
            if (activeCount > 0) {
                val geom = if (activeCount == cachedActivePointCount && cachedActiveGeometry != null) {
                    cachedActiveGeometry!!
                } else {
                    val tempStroke = Stroke.create(
                        id = 0L,
                        points = gestureHandler.activePoints,
                        brush = currentBrush
                    )
                    buildGeometry(tempStroke, activePath).also {
                        cachedActivePointCount = activeCount
                        cachedActiveGeometry = it
                    }
                }
                drawCachedGeometry(geom)
            } else if (cachedActivePointCount != 0) {
                cachedActivePointCount = 0
                cachedActiveGeometry = null
            }

            // Слой 4: Контур лассо
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

        // Слой 5: Закреплённые фотографии (плавающий референс поверх вьюпорта)
        for (i in images.indices) {
            val img = images[i]
            if (!img.isPinned) continue

            val b = if (gestureHandler.previewImageId == img.id && gestureHandler.previewImageBounds != null) {
                gestureHandler.previewImageBounds!!
            } else {
                img.bounds
            }

            val left = b.minX.toFloat()
            val top = b.minY.toFloat()
            val widthPx = (b.maxX - b.minX).toFloat().coerceAtLeast(1f)
            val heightPx = (b.maxY - b.minY).toFloat().coerceAtLeast(1f)

            // Мягкая прямоугольная тень под закреплённой фотографией
            drawRect(
                color = Color(0x28000000),
                topLeft = Offset(left + 2.dp.toPx(), top + 4.dp.toPx()),
                size = Size(widthPx, heightPx)
            )

            val bitmap = imageCache.getOrLoad(boardId, img.fileName)
            if (bitmap != null) {
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(widthPx.roundToInt(), heightPx.roundToInt())
                )
            } else {
                drawRect(
                    color = Color(0x1A1B6EF3),
                    topLeft = Offset(left, top),
                    size = Size(widthPx, heightPx)
                )
            }

            // Постоянная рамка вокруг закреплённой фотографии (когда не выделена; при выделении рисуется синяя рамка HUD с маркерами)
            if (img.id != selectedImageId) {
                drawRect(
                    color = Color(0xFF455A64),
                    topLeft = Offset(left, top),
                    size = Size(widthPx, heightPx),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Экранный HUD выделенной фотографии: синяя рамка и 4 угловых маркера масштабирования
        if (selectedImageId != null) {
            val selImg = images.firstOrNull { it.id == selectedImageId }
            if (selImg != null) {
                val b = if (gestureHandler.previewImageId == selImg.id && gestureHandler.previewImageBounds != null) {
                    gestureHandler.previewImageBounds!!
                } else {
                    selImg.bounds
                }
                val left = if (selImg.isPinned) b.minX.toFloat() else viewportState.worldToScreenX(b.minX)
                val top = if (selImg.isPinned) b.minY.toFloat() else viewportState.worldToScreenY(b.minY)
                val right = if (selImg.isPinned) b.maxX.toFloat() else viewportState.worldToScreenX(b.maxX)
                val bottom = if (selImg.isPinned) b.maxY.toFloat() else viewportState.worldToScreenY(b.maxY)

                drawRect(
                    color = Color(0xFF1B6EF3),
                    topLeft = Offset(left, top),
                    size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )

                val handleRadius = 7.dp.toPx()
                val corners = listOf(
                    Offset(left, top),
                    Offset(right, top),
                    Offset(left, bottom),
                    Offset(right, bottom)
                )
                for (cornerOffset in corners) {
                    drawCircle(color = Color.White, radius = handleRadius, center = cornerOffset)
                    drawCircle(
                        color = Color(0xFF1B6EF3),
                        radius = handleRadius,
                        center = cornerOffset,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                    )
                }
            }
        }

        // Экранный HUD выделения лассо
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

        // Курсор ластика
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