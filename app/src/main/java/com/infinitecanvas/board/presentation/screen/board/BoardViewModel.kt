package com.infinitecanvas.board.presentation.screen.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel экрана бесконечной доски.
 * Управляет положением камеры, списком векторных штрихов, инструментами (кисти/ластик)
 * и историей отмены (Undo) для рисования и стирания.
 */
class BoardViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_OFFSET_X = "viewport_offset_x"
        private const val KEY_OFFSET_Y = "viewport_offset_y"
        private const val KEY_SCALE = "viewport_scale"
        private const val MAX_HISTORY_STEPS = 50
    }

    private val _viewportState = MutableStateFlow(
        ViewportState(
            offsetX = savedStateHandle.get<Double>(KEY_OFFSET_X) ?: 0.0,
            offsetY = savedStateHandle.get<Double>(KEY_OFFSET_Y) ?: 0.0,
            scale = savedStateHandle.get<Double>(KEY_SCALE) ?: 1.0
        )
    )
    val viewportState: StateFlow<ViewportState> = _viewportState.asStateFlow()

    private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
    val strokes: StateFlow<List<Stroke>> = _strokes.asStateFlow()

    private val _activeTool = MutableStateFlow(CanvasTool.BRUSH)
    val activeTool: StateFlow<CanvasTool> = _activeTool.asStateFlow()

    private val _activeBrush = MutableStateFlow(
        Brush(
            type = BrushType.FOUNTAIN,
            colorArgb = 0xFF1A1C1E.toInt(),
            baseSize = 4f,
            opacity = 1f
        )
    )
    val activeBrush: StateFlow<Brush> = _activeBrush.asStateFlow()

    // Стек предыдущих состояний списка штрихов для Undo (поддерживает отмену и рисования, и стирания)
    private val undoStack = ArrayDeque<List<Stroke>>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    fun onTransform(
        panDeltaX: Float,
        panDeltaY: Float,
        zoomFactor: Float,
        centroidX: Float,
        centroidY: Float
    ) {
        _viewportState.update { current ->
            val newScale = ViewportState.clampScale(current.scale * zoomFactor)
            val actualZoom = newScale / current.scale

            val newOffsetX = centroidX - (centroidX - current.offsetX) * actualZoom + panDeltaX
            val newOffsetY = centroidY - (centroidY - current.offsetY) * actualZoom + panDeltaY

            savedStateHandle[KEY_OFFSET_X] = newOffsetX
            savedStateHandle[KEY_OFFSET_Y] = newOffsetY
            savedStateHandle[KEY_SCALE] = newScale

            current.copy(
                offsetX = newOffsetX,
                offsetY = newOffsetY,
                scale = newScale
            )
        }
    }

    fun resetViewport() {
        _viewportState.update {
            savedStateHandle[KEY_OFFSET_X] = 0.0
            savedStateHandle[KEY_OFFSET_Y] = 0.0
            savedStateHandle[KEY_SCALE] = 1.0
            ViewportState(offsetX = 0.0, offsetY = 0.0, scale = 1.0)
        }
    }

    fun onStrokeCommitted(stroke: Stroke) {
        pushUndoSnapshot(_strokes.value)
        _strokes.update { currentList -> currentList + stroke }
    }

    /**
     * Удаляет штрихи с указанными ID при попадании ластика и записывает шаг в историю Undo.
     */
    fun eraseStrokesByIds(idsToErase: Set<Long>) {
        if (idsToErase.isEmpty()) return
        val currentList = _strokes.value
        val filtered = currentList.filterNot { it.id in idsToErase }
        if (filtered.size != currentList.size) {
            pushUndoSnapshot(currentList)
            _strokes.value = filtered
        }
    }

    fun selectBrushType(type: BrushType) {
        _activeTool.value = CanvasTool.BRUSH
        _activeBrush.update { it.copy(type = type) }
    }

    fun selectEraser() {
        _activeTool.value = CanvasTool.ERASER
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        _strokes.value = undoStack.removeLast()
        _canUndo.value = undoStack.isNotEmpty()
    }

    private fun pushUndoSnapshot(snapshot: List<Stroke>) {
        if (undoStack.size >= MAX_HISTORY_STEPS) {
            undoStack.removeFirst()
        }
        undoStack.addLast(snapshot)
        _canUndo.value = true
    }
}