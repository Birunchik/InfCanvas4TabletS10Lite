package com.infinitecanvas.board.presentation.screen.board

import androidx.activity.compose.BackHandler
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
import com.infinitecanvas.board.data.local.storage.ImageAssetStorage
import com.infinitecanvas.board.presentation.screen.board.huds.BoardBottomHud
import com.infinitecanvas.board.presentation.screen.board.huds.BoardMediaHud
import com.infinitecanvas.board.presentation.screen.board.huds.BoardTopBar
import com.infinitecanvas.board.presentation.screen.board.huds.CanvasLayer

// Экран — «склейка» ViewModel и HUD: собственного состояния не хранит, поэтому
// поворот экрана не сбрасывает инструмент и выделение.
@Composable
fun BoardScreen(
    boardId: Long,
    viewModel: BoardViewModel,
    imageAssetStorage: ImageAssetStorage,
    onNavigateBack: () -> Unit
) {
    // Ключ boardId: переход на другую доску без пересоздания экрана должен вызвать
    // openBoard заново (повторный вход в ту же доску ViewModel проигнорирует).
    LaunchedEffect(boardId) {
        viewModel.openBoard(boardId)
    }

    // flush асинхронен: back (системный или кнопка) навигирует только после записи.
    // Флага «выход уже начат» нет, поэтому повторный back запустит flush ещё раз.
    val handleExit: () -> Unit = {
        viewModel.flushSaveAndThumbnail {
            onNavigateBack()
        }
    }

    BackHandler(onBack = handleExit)

    val activeTool by viewModel.activeTool.collectAsState()
    val activeBrush by viewModel.activeBrush.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val presetColors by viewModel.presetColors.collectAsState()
    val selectedPresetIndex by viewModel.selectedPresetIndex.collectAsState()
    val isZoomLocked by viewModel.isZoomLocked.collectAsState()
    val selectedStrokeIds by viewModel.selectedStrokeIds.collectAsState()
    val hasClipboardContent by viewModel.hasClipboardContent.collectAsState()
    val isSelectedImagePinned by viewModel.isSelectedImagePinned.collectAsState()
    // Отдельный поток только со scale: панорамирование не пересобирает BoardScreen,
    // HUD обновляется лишь при смене масштаба.
    val zoomScale by viewModel.zoomScale.collectAsState()

    val hasSelectedImage by viewModel.hasSelectedImage.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        CanvasLayer(
            boardId = boardId,
            viewModel = viewModel,
            imageAssetStorage = imageAssetStorage,
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
                .padding(start = 16.dp, top = 8.dp),
            onNavigateBack = handleExit,
            onSelectBrushType = { viewModel.selectBrushType(it) },
            onSelectEraser = { viewModel.selectEraser() },
            onSelectLasso = { viewModel.selectLassoTool() },
            onUndo = { viewModel.undo() },
            onSetBrushSize = { viewModel.setSnappedBrushSize(it, step = 2f) },
            onSelectColor = { viewModel.selectColor(it) },
            onSelectPresetIndex = { viewModel.selectPresetIndex(it) },
            onRemovePresetColor = { viewModel.removePresetColor(it) },
            onUpdatePresetColor = { index, color -> viewModel.updatePresetColorAt(index, color) },
            onPinColor = { viewModel.pinCurrentColor(it) }
        )

        BoardMediaHud(
            createTempCameraFile = viewModel::createTempCameraFile,
            onImageUriReady = { uri -> viewModel.insertImageFromUri(boardId, uri) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(end = 16.dp, top = 8.dp)
        )

        BoardBottomHud(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            zoom = zoomScale,
            isZoomLocked = isZoomLocked,
            selectedCount = selectedStrokeIds.size,
            hasClipboardContent = hasClipboardContent,
            isSelectedImagePinned = isSelectedImagePinned,
            hasSelectedImage = hasSelectedImage,
            onToggleZoomLock = { viewModel.toggleZoomLock() },
            onResetZoom = viewModel::resetViewport,
            onCopySelected = { viewModel.copySelectedStrokes() },
            onPasteClipboard = { viewModel.pasteClipboardStrokes() },
            onDeleteSelected = { viewModel.deleteSelectedStrokes() },
            onClearSelection = { viewModel.clearSelection() },
            onDeleteSelectedImage = { viewModel.deleteSelectedImage() },
            onTogglePinSelectedImage = { viewModel.togglePinSelectedImage() },
        )
    }
}