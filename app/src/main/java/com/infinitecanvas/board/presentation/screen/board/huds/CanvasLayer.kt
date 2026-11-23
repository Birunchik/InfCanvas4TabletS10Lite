package com.infinitecanvas.board.presentation.screen.board.huds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.presentation.canvas.BoardImageCache
import com.infinitecanvas.board.presentation.canvas.InfiniteCanvasView
import com.infinitecanvas.board.presentation.screen.board.BoardViewModel

@Composable
internal fun CanvasLayer(
    boardId: Long,
    viewModel: BoardViewModel,
    imageStore: ImageStore,
    isZoomLocked: Boolean
) {
    val viewportState by viewModel.viewportState.collectAsState()
    val images by viewModel.images.collectAsState()
    val selectedImageId by viewModel.selectedImageId.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val activeBrush by viewModel.activeBrush.collectAsState()
    val selectedStrokeIds by viewModel.selectedStrokeIds.collectAsState()

    // Добавляем подписки для новых параметров
    val selectionBounds by viewModel.selectionBounds.collectAsState()
    val quadtree by viewModel.quadtree.collectAsState()

    // Bitmap — тяжёлый и не immutable объект, его нельзя класть в StateFlow, поэтому
    // кэш лишь инкрементит тик: Canvas перерисуется, когда картинка догрузится.
    var imageLoadTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    // Ключ — imageStore: кэш не должен переживать смену хранилища, иначе
    // отдавал бы bitmap удалённых или подменённых файлов.
    val imageCache = remember(imageStore) {
        BoardImageCache(
            imageStore = imageStore,
            scope = scope,
            onImageLoaded = { imageLoadTick++ }
        )
    }

    InfiniteCanvasView(
        boardId = boardId,
        isZoomLocked = isZoomLocked,
        viewportState = viewportState,
        images = images,
        selectedImageId = selectedImageId,
        imageCache = imageCache,
        imageLoadTick = imageLoadTick,
        activeTool = activeTool,
        activeBrush = activeBrush,
        selectedStrokeIds = selectedStrokeIds,
        selectionBounds = selectionBounds,
        quadtree = quadtree,
        // Палец не рисует: одиночный touch уходит в pan/zoom (если не попал в картинку),
        // иначе ладонь оставляла бы штрихи при работе пером.
        allowFingerDrawing = false,
        onCanvasViewportSizeChanged = viewModel::updateCanvasViewportSize,
        onStrokeCommitted = viewModel::onStrokeCommitted,
        onEraseStrokes = viewModel::eraseStrokesByIds,
        onStrokesSelected = viewModel::selectStrokes,
        onMoveSelectedStrokes = viewModel::moveSelectedStrokes,
        onImageSelected = viewModel::selectImage,
        onUpdateImageBounds = viewModel::updateImageBounds,
        onTransform = viewModel::onTransform
    )
}