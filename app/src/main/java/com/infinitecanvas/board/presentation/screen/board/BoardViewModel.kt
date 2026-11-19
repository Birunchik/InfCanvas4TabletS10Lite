package com.infinitecanvas.board.presentation.screen.board

import android.annotation.SuppressLint
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitecanvas.board.data.local.storage.ImageAssetStorage
import com.infinitecanvas.board.data.local.storage.ThumbnailGenerator
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.domain.repository.BoardRepository
import com.infinitecanvas.board.presentation.screen.board.viewControllers.BoardImageController
import com.infinitecanvas.board.presentation.screen.board.viewControllers.BoardStrokeController
import com.infinitecanvas.board.presentation.screen.board.viewControllers.BoardToolController
import com.infinitecanvas.board.presentation.screen.board.viewControllers.BoardViewportController
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

// Контроллеры — единственный источник состояния; ViewModel не держит копий
// штрихов/картинок и лишь связывает их колбэками, поэтому flush читает их снапшоты.
class BoardViewModel(
    private val repository: BoardRepository,
    private val thumbnailGenerator: ThumbnailGenerator,
    imageAssetStorage: ImageAssetStorage
) : ViewModel() {

    private var currentBoardId: Long? = null
    // Один активный Job на все правки: scheduleAutoSave отменяет предыдущий,
    // то есть это дебаунс, а не очередь последовательных записей.
    private var autoSaveJob: Job? = null
    private var hasUnsavedChanges: Boolean = false

    private val _boardTitle = MutableStateFlow("")

    private val _isLoading = MutableStateFlow(true)

    private val viewportController = BoardViewportController(
        onViewportChanged = { scheduleAutoSave(regenerateThumbnail = false) }
    )

    private val strokeController: BoardStrokeController = BoardStrokeController(
        scope = viewModelScope,
        onContentChanged = { scheduleAutoSave(regenerateThumbnail = true) },
        onSelectionActivated = { imageController.clearSelection() }
    )

    private val imageController: BoardImageController = BoardImageController(
        imageAssetStorage = imageAssetStorage,
        scope = viewModelScope,
        viewportController = viewportController,
        onContentChanged = { scheduleAutoSave(regenerateThumbnail = true) },
        onSelectionActivated = { strokeController.clearSelection() }
    )

    private val toolController = BoardToolController(
        onClearSelectionRequested = {
            strokeController.clearSelection()
            imageController.clearSelection()
        }
    )

    // --- Проксируем StateFlow для UI без изменения публичного API ---
    val viewportState = viewportController.viewportState
    val isZoomLocked = viewportController.isZoomLocked

    // Только масштаб — для BoardScreen (перерисовка только при zoom, не при pan).
    val zoomScale: StateFlow<Double> = viewportState
        .map { it.scale }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)

    val strokes = strokeController.strokes
    val selectedStrokeIds = strokeController.selectedStrokeIds
    val selectionBounds = strokeController.selectionBounds
    val quadtree = strokeController.quadtree
    val canUndo = strokeController.canUndo
    val hasClipboardContent = strokeController.hasClipboardContent

    val images = imageController.images
    val selectedImageId = imageController.selectedImageId
    val hasSelectedImage = imageController.hasSelectedImage

    val isSelectedImagePinned = imageController.isSelectedImagePinned

    val activeTool = toolController.activeTool
    val activeBrush = toolController.activeBrush
    val presetColors = toolController.presetColors
    val selectedPresetIndex = toolController.selectedPresetIndex

    // --- Жизненный цикл и сохранение доски ---
    // Повторное открытие уже загруженной доски — no-op: setInitial* сбросил бы
    // undo-стек и выделение, хотя пользователь просто вернулся на тот же boardId.
    fun openBoard(boardId: Long) {
        if (currentBoardId == boardId && !_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val doc = repository.loadBoard(boardId)
            if (doc != null) {
                currentBoardId = boardId
                _boardTitle.value = doc.summary.title
                strokeController.setInitialStrokes(doc.strokes)
                imageController.setInitialImages(doc.images)
                viewportController.setViewport(doc.viewport)
                hasUnsavedChanges = false
            }
            _isLoading.value = false
        }
    }

    // Снапшоты снимаются до launch: пока идёт запись, пользователь может рисовать,
    // а сохраняться должно состояние на момент выхода.
    fun flushSaveAndThumbnail(onFinished: (() -> Unit)? = null) {
        val boardId = currentBoardId ?: run {
            onFinished?.invoke()
            return
        }
        // Отменяем отложенный автосейв: иначе после flush сработает ещё одна запись
        // той же доски, и порядок «кто последний» станет неопределённым.
        autoSaveJob?.cancel()
        val strokesSnapshot = strokes.value
        val imagesSnapshot = images.value
        val viewportSnapshot = viewportState.value

        viewModelScope.launch {
            // NonCancellable: отмена scope (onCleared) не должна оборвать запись на середине —
            // файл и строка Room пишутся без общей транзакции, и половина хуже отсутствия.
            withContext(NonCancellable) {
                val thumbPath = thumbnailGenerator.generateAndSave(
                    boardId = boardId,
                    strokes = strokesSnapshot,
                    images = imagesSnapshot
                )
                repository.saveBoardContent(
                    boardId = boardId,
                    strokes = strokesSnapshot,
                    images = imagesSnapshot,
                    viewport = viewportSnapshot,
                    thumbnailPath = thumbPath
                )
                hasUnsavedChanges = false
            }
            onFinished?.invoke()
        }
    }

    // Дебаунс 1.5 с: таймер перезапускается на каждое изменение. Миниатюра — только
    // при контентных правках: пан/зум меняют лишь viewport и её не требуют.
    private fun scheduleAutoSave(regenerateThumbnail: Boolean) {
        val boardId = currentBoardId ?: return
        hasUnsavedChanges = true
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1_500L.milliseconds)
            val strokesSnapshot = strokes.value
            val imagesSnapshot = images.value
            val viewportSnapshot = viewportState.value
            val thumbPath = if (regenerateThumbnail) {
                thumbnailGenerator.generateAndSave(
                    boardId = boardId,
                    strokes = strokesSnapshot,
                    images = imagesSnapshot
                )
            } else {
                null
            }
            repository.saveBoardContent(
                boardId = boardId,
                strokes = strokesSnapshot,
                images = imagesSnapshot,
                viewport = viewportSnapshot,
                thumbnailPath = thumbPath
            )
            hasUnsavedChanges = false
        }
    }

    // Размер и кламп идут парой: controller запоминает размер экрана (нужен вставке
    // картинок и отпину), а pinned-картинки сразу вписываются в новые границы.
    fun updateCanvasViewportSize(widthPx: Int, heightPx: Int) {
        viewportController.updateCanvasSize(widthPx, heightPx)
        imageController.clampPinnedImagesToViewport(widthPx, heightPx)
    }

    fun onTransform(panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) =
        viewportController.onTransform(panX, panY, zoom, centroidX, centroidY)

    fun toggleZoomLock() = viewportController.toggleZoomLock()
    fun resetViewport() = viewportController.resetViewport()

    fun createTempCameraFile(): File = imageController.createTempCameraFile()
    // boardId может прийти раньше openBoard (камера/галерея на старте), поэтому
    // запоминаем его — иначе автосейв не узнает текущую доску.
    fun insertImageFromUri(boardId: Long, uri: Uri) {
        if (boardId > 0L && currentBoardId == null) currentBoardId = boardId
        imageController.insertImageFromUri(boardId, uri)
    }

    fun selectImage(imageId: Long?) = imageController.selectImage(imageId)
    fun updateImageBounds(imageId: Long, newBounds: WorldBounds) =
        imageController.updateImageBounds(imageId, newBounds)

    fun deleteSelectedImage() {
        val boardId = currentBoardId ?: return
        imageController.deleteSelectedImage(boardId)
    }
    fun togglePinSelectedImage() = imageController.togglePinSelectedImage()

    fun selectBrushType(type: BrushType) = toolController.selectBrushType(type)
    fun selectEraser() = toolController.selectEraser()
    fun selectLassoTool() = toolController.selectLassoTool()
    fun selectColor(colorArgb: Int) = toolController.selectColor(colorArgb)
    fun selectPresetIndex(index: Int?) = toolController.selectPresetIndex(index)
    fun updatePresetColorAt(index: Int, newColorArgb: Int) =
        toolController.updatePresetColorAt(index, newColorArgb)

    fun pinCurrentColor(colorArgb: Int) = toolController.pinCurrentColor(colorArgb)
    fun removePresetColor(index: Int) = toolController.removePresetColor(index)
    fun setSnappedBrushSize(rawSize: Float, step: Float = 2f) =
        toolController.setSnappedBrushSize(rawSize, step)

    fun selectStrokes(ids: Set<Long>) = strokeController.selectStrokes(ids)
    fun deleteSelectedStrokes() = strokeController.deleteSelectedStrokes()
    fun moveSelectedStrokes(dx: Double, dy: Double) = strokeController.moveSelectedStrokes(dx, dy)
    fun copySelectedStrokes() = strokeController.copySelectedStrokes()
    fun pasteClipboardStrokes() = strokeController.pasteClipboardStrokes()
    fun onStrokeCommitted(stroke: Stroke) = strokeController.onStrokeCommitted(stroke)
    fun eraseStrokesByIds(idsToRemove: Set<Long>) = strokeController.eraseStrokesByIds(idsToRemove)
    fun undo() = strokeController.undo()

    fun clearSelection() {
        strokeController.clearSelection()
        imageController.clearSelection()
    }

    @SuppressLint("EmptySuperCall")
    // Этот flush уже не сработает: viewModelScope отменяется до onCleared, и launch
    // внутри не выполнится. Надёжное сохранение — только явный выход через handleExit.
    override fun onCleared() {
        super.onCleared()
        if (hasUnsavedChanges) {
            flushSaveAndThumbnail()
        }
    }
}