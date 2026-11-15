package com.infinitecanvas.board.presentation.screen.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.presentation.screen.board.huds.BoardBottomHud
import com.infinitecanvas.board.presentation.screen.board.huds.BoardTopBar
import com.infinitecanvas.board.presentation.screen.board.huds.CanvasLayer

@Composable
fun BoardScreen(
    boardId: Long,
    viewModel: BoardViewModel,
    onNavigateBack: () -> Unit
) {
    val activeTool by viewModel.activeTool.collectAsState()
    val activeBrush by viewModel.activeBrush.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val presetColors by viewModel.presetColors.collectAsState()
    val selectedPresetIndex by viewModel.selectedPresetIndex.collectAsState()
    val isZoomLocked by viewModel.isZoomLocked.collectAsState()
    val selectedStrokeIds by viewModel.selectedStrokeIds.collectAsState()
    val hasClipboardContent by viewModel.hasClipboardContent.collectAsState()
    val viewportState by viewModel.viewportState.collectAsState()

    LaunchedEffect(boardId) {
        viewModel.openBoard(boardId)
    }

    val handleExit: () -> Unit = {
        viewModel.flushSaveAndThumbnail {
            onNavigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CanvasLayer(
            viewModel = viewModel,
            isZoomLocked = isZoomLocked
        )

        BoardTopBar(
            selectedPresetIndex = selectedPresetIndex,
            activeTool = activeTool,
            activeBrush = activeBrush,
            canUndo = canUndo,
            presetColors = presetColors,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 96.dp, top = 0.dp),
            onSelectBrushType = { viewModel.selectBrushType(it) },
            onSelectEraser = { viewModel.selectEraser() },
            onSelectLasso = { viewModel.selectLassoTool() },
            onUndo = { viewModel.undo() },
            onSetBrushSize = { viewModel.setSnappedBrushSize(it, step = 2f) },
            onSelectColor = { viewModel.selectColor(it) },
            onSelectPresetIndex = { viewModel.selectPresetIndex(it) },
            onRemovePresetColor = { viewModel.removePresetColor(it) },
            onUpdatePresetColor = { index, color -> viewModel.updatePresetColorAt(index, color) },
            onPinColor = { viewModel.pinCurrentColor(it) },
            onNavigateBack = handleExit
        )

        BoardBottomHud(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            zoom = viewportState.scale,
            isZoomLocked = isZoomLocked,
            selectedCount = selectedStrokeIds.size,
            hasClipboardContent = hasClipboardContent,
            onToggleZoomLock = { viewModel.toggleZoomLock() },
            onResetZoom = viewModel::resetViewport,
            onCopySelected = { viewModel.copySelectedStrokes() },
            onPasteClipboard = { viewModel.pasteClipboardStrokes() },
            onDeleteSelected = { viewModel.deleteSelectedStrokes() },
            onClearSelection = { viewModel.clearSelection() }
        )
    }
}