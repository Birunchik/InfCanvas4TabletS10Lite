package com.infinitecanvas.board.presentation.screen.board.huds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.infinitecanvas.board.presentation.canvas.InfiniteCanvasView
import com.infinitecanvas.board.presentation.screen.board.BoardViewModel

@Composable
internal fun CanvasLayer(
    viewModel: BoardViewModel,
    isZoomLocked: Boolean
) {
    val viewportState by viewModel.viewportState.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val activeBrush by viewModel.activeBrush.collectAsState()
    val selectedStrokeIds by viewModel.selectedStrokeIds.collectAsState()

    InfiniteCanvasView(
        isZoomLocked = isZoomLocked,
        viewportState = viewportState,
        strokes = strokes,
        activeTool = activeTool,
        activeBrush = activeBrush,
        selectedStrokeIds = selectedStrokeIds,
        allowFingerDrawing = false,
        onStrokeCommitted = viewModel::onStrokeCommitted,
        onEraseStrokes = viewModel::eraseStrokesByIds,
        onStrokesSelected = viewModel::selectStrokes,
        onMoveSelectedStrokes = viewModel::moveSelectedStrokes,
        onTransform = viewModel::onTransform
    )
}