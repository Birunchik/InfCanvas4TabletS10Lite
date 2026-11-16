package com.infinitecanvas.board.presentation.screen.board.huds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.infinitecanvas.board.data.local.storage.ImageAssetStorage
import com.infinitecanvas.board.presentation.canvas.BoardImageCache
import com.infinitecanvas.board.presentation.canvas.InfiniteCanvasView
import com.infinitecanvas.board.presentation.screen.board.BoardViewModel

@Composable
internal fun CanvasLayer(
    boardId: Long,
    viewModel: BoardViewModel,
    imageAssetStorage: ImageAssetStorage,
    isZoomLocked: Boolean
) {
    val viewportState by viewModel.viewportState.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val images by viewModel.images.collectAsState()
    val selectedImageId by viewModel.selectedImageId.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val activeBrush by viewModel.activeBrush.collectAsState()
    val selectedStrokeIds by viewModel.selectedStrokeIds.collectAsState()

    var imageLoadTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val imageCache = remember(imageAssetStorage) {
        BoardImageCache(
            imageAssetStorage = imageAssetStorage,
            scope = scope,
            onImageLoaded = { imageLoadTick++ }
        )
    }

    InfiniteCanvasView(
        boardId = boardId,
        isZoomLocked = isZoomLocked,
        viewportState = viewportState,
        strokes = strokes,
        images = images,
        selectedImageId = selectedImageId,
        imageCache = imageCache,
        imageLoadTick = imageLoadTick,
        activeTool = activeTool,
        activeBrush = activeBrush,
        selectedStrokeIds = selectedStrokeIds,
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