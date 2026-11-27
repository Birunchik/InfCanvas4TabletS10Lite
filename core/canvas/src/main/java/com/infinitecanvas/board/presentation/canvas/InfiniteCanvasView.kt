package com.infinitecanvas.board.presentation.canvas

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.domain.spatial.Quadtree
import com.infinitecanvas.board.presentation.canvas.gestures.canvasGestures
import com.infinitecanvas.board.presentation.canvas.gestures.rememberCanvasGestureHandler
import com.infinitecanvas.board.presentation.canvas.renderer.drawActiveStroke
import com.infinitecanvas.board.presentation.canvas.renderer.drawBatchedWorldGrid
import com.infinitecanvas.board.presentation.canvas.renderer.drawLassoPreview
import com.infinitecanvas.board.presentation.canvas.renderer.drawPinnedImages
import com.infinitecanvas.board.presentation.canvas.renderer.drawScreenOverlays
import com.infinitecanvas.board.presentation.canvas.renderer.drawStrokeLayers
import com.infinitecanvas.board.presentation.canvas.renderer.drawUnpinnedImages
import com.infinitecanvas.board.presentation.canvas.renderer.rememberCanvasRenderState
import kotlin.math.abs

/**
 * Весь холст — один Canvas: слои рисуются вручную в фиксированном порядке,
 * чтобы на кадр приходился один draw-проход и одна композиция, а не семь слоёв.
 */
@Composable
fun InfiniteCanvasView(
    quadtree: Quadtree,
    boardId: Long,
    viewportState: ViewportState,
    images: List<BoardImage>,
    selectedImageId: Long?,
    imageCache: BoardImageCache,
    imageLoadTick: Int = 0,
    activeTool: CanvasTool,
    activeBrush: Brush,
    selectedStrokeIds: Set<Long>,
    selectionBounds: WorldBounds?,
    isZoomLocked: Boolean = false,
    allowFingerDrawing: Boolean = false,
    onCanvasViewportSizeChanged: (widthPx: Int, heightPx: Int) -> Unit,
    /**
     * Источник id для коммитимого штриха — обязателен намеренно.
     *
     * Порядок наложения штрихов держится на `id` (сортировка в draw-фазе), поэтому
     * значения обязаны быть уникальными и монотонными. Дефолт с `System.nanoTime()`
     * давал бы вызывающему молчаливо неверный контракт; в приложении сюда приходит
     * генератор доски (`BoardViewModel.nextStrokeId`).
     */
    nextStrokeId: () -> Long,
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
    val renderState = rememberCanvasRenderState()
    val interactionState = rememberCanvasInteractionState()

    val gestureHandler = rememberCanvasGestureHandler(
        viewportState = viewportState,
        activeTool = activeTool,
        activeBrush = activeBrush,
        selectedStrokeIds = selectedStrokeIds,
        selectionBounds = selectionBounds,
        images = images,
        selectedImageId = selectedImageId,
        quadtree = quadtree,
        isZoomLocked = isZoomLocked,
        allowFingerDrawing = allowFingerDrawing,
        renderState = renderState,
        interactionState = interactionState,
        nextStrokeId = nextStrokeId,
        onStrokeCommitted = onStrokeCommitted,
        onEraseStrokes = onEraseStrokes,
        onStrokesSelected = onStrokesSelected,
        onMoveSelectedStrokes = onMoveSelectedStrokes,
        onImageSelected = onImageSelected,
        onUpdateImageBounds = onUpdateImageBounds,
        onTransform = onTransform
    )

    // Нижняя граница масштаба: при scale → 0 ширина лассо (2.5f / scale) улетела бы в бесконечность.
    val currentScaleFloat = viewportState.scale.toFloat().coerceAtLeast(0.1f)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                onCanvasViewportSizeChanged(size.width, size.height)
            }
            .background(backgroundColor)
            .canvasGestures(gestureHandler, renderState.pathCache)
    ) {
        // Читаем activeTick: Canvas перерисуется при мутации gesture handler.
        // Мутация состояния сама по себе отрисовку не инвалидирует — подписка возникает только при чтении в draw-фазе.
        @Suppress("UNUSED_EXPRESSION")
        renderState.activeTick
        // Читаем imageLoadTick: Canvas перерисуется после подгрузки битмапов.
        @Suppress("UNUSED_EXPRESSION")
        imageLoadTick

        val canvasWidth = size.width
        val canvasHeight = size.height

        val worldLeft = viewportState.screenToWorldX(0f)
        val worldTop = viewportState.screenToWorldY(0f)
        val worldRight = viewportState.screenToWorldX(canvasWidth)
        val worldBottom = viewportState.screenToWorldY(canvasHeight)

        // 1. Сетка мира
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
            minorGridPath = renderState.minorGridPath,
            majorGridPath = renderState.majorGridPath
        )

        val isDraggingNow = selectedStrokeIds.isNotEmpty() &&
                (gestureHandler.selectionDragDx != 0.0 || gestureHandler.selectionDragDy != 0.0)

        // Расширяем запрос на величину драга: выделенные штрихи рисуются со сдвигом, а их bounds
        // в quadtree и модели ещё старые — иначе у края экрана они выпадали бы из видимого набора.
        val queryBounds = WorldBounds(
            minX = worldLeft - abs(gestureHandler.selectionDragDx),
            minY = worldTop - abs(gestureHandler.selectionDragDy),
            maxX = worldRight + abs(gestureHandler.selectionDragDx),
            maxY = worldBottom + abs(gestureHandler.selectionDragDy)
        )
        quadtree.query(queryBounds, renderState.visibleStrokesBuffer)
        // Порядок наложения обязан быть стабильным между кадрами, а query обходит узлы
        // дерева (порядок зависит от раскладки, а не от времени рисования).
        // Сортируем здесь, но только когда состав набора изменился: sortBy на каждом
        // кадре — это аллокация списка + O(n log n) в draw-фазе, т.е. ровно то,
        // от чего защищает инвариант «не аллоцировать».
        renderState.sortVisibleStrokesIfChanged()

        // translate + scale с pivot = Offset.Zero повторяет ViewportState.worldToScreen*:
        // менять формулы здесь нельзя, иначе штрихи разъедутся с сеткой, нарисованной в screen-space.
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
            // 2. Незакреплённые фотографии (под штрихами)
            drawUnpinnedImages(
                images = images,
                boardId = boardId,
                imageCache = imageCache,
                gestureHandler = gestureHandler,
                worldLeft = worldLeft,
                worldTop = worldTop,
                worldRight = worldRight,
                worldBottom = worldBottom
            )

            // 3. Штрихи (highlighters → ручки/перья, с драгом выделенных)
            drawStrokeLayers(
                visibleStrokes = renderState.visibleStrokesBuffer,
                selectedStrokeIds = selectedStrokeIds,
                isDraggingNow = isDraggingNow,
                selectionDragDx = gestureHandler.selectionDragDx,
                selectionDragDy = gestureHandler.selectionDragDy,
                pathCache = renderState.pathCache,
                draggedStrokesBuffer = renderState.draggedStrokesBuffer,
                visibleWorldBounds = queryBounds
            )

            // 4. Активный (рисуемый сейчас) штрих
            // До коммита его нет в quadtree, поэтому он не попадает в visibleStrokesBuffer
            // и рисуется отдельным проходом поверх закоммиченных.
            drawActiveStroke(
                activePointCount = gestureHandler.activePoints.size,
                createStroke = {
                    Stroke.create(
                        id = 0L,
                        points = gestureHandler.activePoints,
                        brush = activeBrush
                    )
                },
                activePath = renderState.activePath,
                cache = renderState.activeStrokeCache
            )

            // 5. Контур лассо
            if (gestureHandler.lassoPoints.size > 1) {
                drawLassoPreview(
                    lassoWorldPath = gestureHandler.lassoWorldPath,
                    currentScaleFloat = currentScaleFloat,
                    worldDashEffect = renderState.worldDashEffect(currentScaleFloat)
                )
            }
        }

        // Pinned — экранный слой: вне withTransform, поэтому не масштабируется и не панорамируется с холстом.
        // 6. Закреплённые фотографии (floating reference поверх мира)
        drawPinnedImages(
            images = images,
            boardId = boardId,
            imageCache = imageCache,
            gestureHandler = gestureHandler,
            selectedImageId = selectedImageId
        )

        // 7. Экранные оверлеи: HUD фото, HUD лассо, курсор ластика
        drawScreenOverlays(
            viewportState = viewportState,
            gestureHandler = gestureHandler,
            selectedImageId = selectedImageId,
            images = images,
            selectionBounds = selectionBounds,
            selectedStrokeIds = selectedStrokeIds,
            screenDashEffect = renderState.screenDashEffect,
            imageHandleCorners = renderState.imageHandleCorners
        )
    }
}