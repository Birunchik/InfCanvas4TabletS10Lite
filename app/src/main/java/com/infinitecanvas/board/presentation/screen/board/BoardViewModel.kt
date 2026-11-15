package com.infinitecanvas.board.presentation.screen.board

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitecanvas.board.data.local.storage.ThumbnailGenerator
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.repository.BoardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class BoardViewModel(
    private val repository: BoardRepository,
    private val thumbnailGenerator: ThumbnailGenerator
) : ViewModel() {

    private var currentBoardId: Long? = null
    private var autoSaveJob: Job? = null
    private var hasUnsavedChanges: Boolean = false

    private val _boardTitle = MutableStateFlow("")
    val boardTitle: StateFlow<String> = _boardTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _viewportState = MutableStateFlow(ViewportState())
    val viewportState: StateFlow<ViewportState> = _viewportState.asStateFlow()

    private val _isZoomLocked = MutableStateFlow(false)
    val isZoomLocked: StateFlow<Boolean> = _isZoomLocked.asStateFlow()

    private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
    val strokes: StateFlow<List<Stroke>> = _strokes.asStateFlow()

    private val _activeTool = MutableStateFlow(CanvasTool.BRUSH)
    val activeTool: StateFlow<CanvasTool> = _activeTool.asStateFlow()

    private val _activeBrush = MutableStateFlow(
        Brush(
            type = BrushType.BALLPOINT,
            colorArgb = 0xFF1A1C1E.toInt(),
            baseSize = 4f
        )
    )
    val activeBrush: StateFlow<Brush> = _activeBrush.asStateFlow()

    private val _selectedStrokeIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedStrokeIds: StateFlow<Set<Long>> = _selectedStrokeIds.asStateFlow()

    private val _presetColors = MutableStateFlow(
        listOf(
            0xFF1A1C1E.toInt(),
            0xFFD93025.toInt(),
            0xFF1A73E8.toInt(),
            0xFF1E8E3E.toInt()
        )
    )
    val presetColors: StateFlow<List<Int>> = _presetColors.asStateFlow()

    private val _selectedPresetIndex = MutableStateFlow<Int?>(0)
    val selectedPresetIndex: StateFlow<Int?> = _selectedPresetIndex.asStateFlow()

    private val undoStack = ArrayDeque<List<Stroke>>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _clipboardStrokes = MutableStateFlow<List<Stroke>>(emptyList())
    val hasClipboardContent: StateFlow<Boolean> = _clipboardStrokes
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    private var pasteCascadeCount = 0

    /**
     * Загружает доску по идентификатору из репозитория.
     */
    fun openBoard(boardId: Long) {
        if (currentBoardId == boardId && !_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            undoStack.clear()
            _canUndo.value = false
            _selectedStrokeIds.value = emptySet()

            val doc = repository.loadBoard(boardId)
            if (doc != null) {
                currentBoardId = boardId
                _boardTitle.value = doc.summary.title
                _strokes.value = doc.strokes
                _viewportState.value = doc.viewport
                hasUnsavedChanges = false
            }
            _isLoading.value = false
        }
    }

    /**
     * Принудительно сохраняет доску и обновляет превью (вызывать при закрытии доски или onStop).
     */
    fun flushSaveAndThumbnail(onFinished: (() -> Unit)? = null) {
        val boardId = currentBoardId ?: run {
            onFinished?.invoke()
            return
        }
        autoSaveJob?.cancel()
        val strokesSnapshot = _strokes.value
        val viewportSnapshot = _viewportState.value

        viewModelScope.launch {
            withContext(NonCancellable) {
                val thumbPath = thumbnailGenerator.generateAndSave(boardId, strokesSnapshot)
                repository.saveBoardContent(
                    boardId = boardId,
                    strokes = strokesSnapshot,
                    viewport = viewportSnapshot,
                    thumbnailPath = thumbPath
                )
                hasUnsavedChanges = false
            }
            onFinished?.invoke()
        }
    }

    private fun scheduleAutoSave(regenerateThumbnail: Boolean) {
        val boardId = currentBoardId ?: return
        hasUnsavedChanges = true
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1_500L.milliseconds)
            val strokesSnapshot = _strokes.value
            val viewportSnapshot = _viewportState.value
            val thumbPath = if (regenerateThumbnail) {
                thumbnailGenerator.generateAndSave(boardId, strokesSnapshot)
            } else {
                null
            }
            repository.saveBoardContent(
                boardId = boardId,
                strokes = strokesSnapshot,
                viewport = viewportSnapshot,
                thumbnailPath = thumbPath
            )
            hasUnsavedChanges = false
        }
    }

    fun renameCurrentBoard(newTitle: String) {
        val boardId = currentBoardId ?: return
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        _boardTitle.value = trimmed
        viewModelScope.launch {
            repository.renameBoard(boardId, trimmed)
        }
    }

    fun selectBrushType(type: BrushType) {
        _activeTool.value = CanvasTool.BRUSH
        _activeBrush.update { it.copy(type = type) }
    }

    fun selectEraser() {
        _activeTool.value = CanvasTool.ERASER
        _selectedStrokeIds.value = emptySet()
    }

    fun selectLassoTool() {
        _activeTool.value = CanvasTool.LASSO
    }

    fun selectStrokes(ids: Set<Long>) {
        _selectedStrokeIds.value = ids
    }

    fun clearSelection() {
        _selectedStrokeIds.value = emptySet()
    }

    fun deleteSelectedStrokes() {
        val ids = _selectedStrokeIds.value
        if (ids.isEmpty()) return
        val currentList = _strokes.value
        val filtered = currentList.filterNot { it.id in ids }
        if (filtered.size != currentList.size) {
            pushUndoSnapshot(currentList)
            _strokes.value = filtered
            scheduleAutoSave(regenerateThumbnail = true)
        }
        _selectedStrokeIds.value = emptySet()
    }

    fun moveSelectedStrokes(dx: Double, dy: Double) {
        val ids = _selectedStrokeIds.value
        if (ids.isEmpty() || (dx == 0.0 && dy == 0.0)) return
        val currentList = _strokes.value
        pushUndoSnapshot(currentList)
        _strokes.value = currentList.map { stroke ->
            if (stroke.id in ids) stroke.translated(dx, dy) else stroke
        }
        scheduleAutoSave(regenerateThumbnail = true)
    }

    fun copySelectedStrokes() {
        val ids = _selectedStrokeIds.value
        if (ids.isEmpty()) return
        val copied = _strokes.value.filter { it.id in ids }
        if (copied.isNotEmpty()) {
            _clipboardStrokes.value = copied
            pasteCascadeCount = 0
        }
    }

    fun pasteClipboardStrokes() {
        val clipboard = _clipboardStrokes.value
        if (clipboard.isEmpty()) return

        pasteCascadeCount++
        val offsetStep = 28.0 * pasteCascadeCount
        val baseId = System.nanoTime()

        val pastedStrokes = clipboard.mapIndexed { index, stroke ->
            stroke
                .translated(offsetStep, offsetStep)
                .copy(id = baseId + index + 1L)
        }

        val currentList = _strokes.value
        pushUndoSnapshot(currentList)
        _strokes.value = currentList + pastedStrokes
        _selectedStrokeIds.value = pastedStrokes.mapTo(HashSet(pastedStrokes.size)) { it.id }
        scheduleAutoSave(regenerateThumbnail = true)
    }

    fun selectColor(colorArgb: Int) {
        _activeBrush.update { it.copy(colorArgb = colorArgb) }
    }

    fun selectPresetIndex(index: Int?) {
        _selectedPresetIndex.value = index
    }

    fun updatePresetColorAt(index: Int, newColorArgb: Int) {
        _presetColors.update { current ->
            if (index in current.indices) {
                current.toMutableList().apply { set(index, newColorArgb) }
            } else {
                current
            }
        }
        _activeBrush.update { it.copy(colorArgb = newColorArgb) }
    }

    fun pinCurrentColor(colorArgb: Int) {
        _presetColors.update { current ->
            if (current.contains(colorArgb)) {
                current
            } else if (current.size < 4) {
                current + colorArgb
            } else {
                listOf(current[0], current[2], current[3], colorArgb)
            }
        }
        val newList = _presetColors.value
        _selectedPresetIndex.value = newList.indexOf(colorArgb).takeIf { it >= 0 }
        _activeBrush.update { it.copy(colorArgb = colorArgb) }
    }

    fun removePresetColor(index: Int) {
        val current = _presetColors.value
        if (current.size <= 1 || index !in current.indices) return
        val updated = current.toMutableList().apply { removeAt(index) }
        _presetColors.value = updated
        if (_selectedPresetIndex.value == index) {
            _selectedPresetIndex.value = null
        }
    }

    fun setSnappedBrushSize(rawSize: Float, step: Float = 2f) {
        val snapped = (kotlin.math.round(rawSize / step) * step).coerceIn(2f, 24f)
        _activeBrush.update { it.copy(baseSize = snapped) }
    }

    fun onStrokeCommitted(stroke: Stroke) {
        val currentList = _strokes.value
        pushUndoSnapshot(currentList)
        _strokes.value = currentList + stroke
        scheduleAutoSave(regenerateThumbnail = true)
    }

    fun eraseStrokesByIds(idsToRemove: Set<Long>) {
        if (idsToRemove.isEmpty()) return
        val currentList = _strokes.value
        val filtered = currentList.filterNot { it.id in idsToRemove }
        if (filtered.size != currentList.size) {
            pushUndoSnapshot(currentList)
            _strokes.value = filtered
            scheduleAutoSave(regenerateThumbnail = true)
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        _strokes.value = previous
        _canUndo.value = undoStack.isNotEmpty()
        _selectedStrokeIds.value = emptySet()
        scheduleAutoSave(regenerateThumbnail = true)
    }

    fun onTransform(
        panX: Float,
        panY: Float,
        zoom: Float,
        centroidX: Float,
        centroidY: Float
    ) {
        _viewportState.update { state ->
            val effectiveZoom = if (_isZoomLocked.value) 1.0 else zoom.toDouble()
            val newScale = (state.scale * effectiveZoom).coerceIn(0.15, 8.0)
            val actualZoom = newScale / state.scale

            val newOffsetX = centroidX - (centroidX - state.offsetX) * actualZoom + panX
            val newOffsetY = centroidY - (centroidY - state.offsetY) * actualZoom + panY

            state.copy(
                offsetX = newOffsetX,
                offsetY = newOffsetY,
                scale = newScale
            )
        }
        scheduleAutoSave(regenerateThumbnail = false)
    }

    fun toggleZoomLock() {
        _isZoomLocked.update { !it }
    }

    fun resetViewport() {
        _viewportState.value = ViewportState()
        scheduleAutoSave(regenerateThumbnail = false)
    }

    private fun pushUndoSnapshot(snapshot: List<Stroke>) {
        if (undoStack.size >= 40) {
            undoStack.removeFirst()
        }
        undoStack.addLast(snapshot)
        _canUndo.value = true
    }

    @SuppressLint("EmptySuperCall")
    override fun onCleared() {
        super.onCleared()
        if (hasUnsavedChanges) {
            flushSaveAndThumbnail()
        }
    }
}