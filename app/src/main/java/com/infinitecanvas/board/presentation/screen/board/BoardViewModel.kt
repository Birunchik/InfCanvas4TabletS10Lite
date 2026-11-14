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
import kotlin.math.roundToInt

/**
 * ViewModel экрана бесконечной доски.
 * Управляет положением камеры, списком штрихов, инструментами и историей Undo.
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

    // Состояние вьюпорта (камеры)
    private val _viewportState = MutableStateFlow(
        ViewportState(
            offsetX = savedStateHandle.get<Double>(KEY_OFFSET_X) ?: 0.0,
            offsetY = savedStateHandle.get<Double>(KEY_OFFSET_Y) ?: 0.0,
            scale = savedStateHandle.get<Double>(KEY_SCALE) ?: 1.0
        )
    )
    val viewportState: StateFlow<ViewportState> = _viewportState.asStateFlow()

    // Штрихи на холсте
    private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
    val strokes: StateFlow<List<Stroke>> = _strokes.asStateFlow()

    // Активный инструмент и кисть
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

    // История для Undo
    private val undoStack = ArrayDeque<List<Stroke>>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _presetColors = MutableStateFlow(
        mutableListOf(
            0xFF1A1C1E.toInt(), // Чёрный
            0xFFB3261E.toInt(), // Красный
            0xFF1B6EF3.toInt(), // Синий
            0xFF006C4C.toInt(), // Зелёный
            0xFF6750A4.toInt()  // Фиолетовый
        )
    )
    val presetColors: StateFlow<List<Int>> = _presetColors.asStateFlow()

    //region Viewport Actions
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
    //endregion

    //region Drawing & Editing Actions
    fun onStrokeCommitted(stroke: Stroke) {
        pushUndoSnapshot(_strokes.value)
        _strokes.update { currentList -> currentList + stroke }
    }

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
    //endregion

    //region Color & Brush Customization
    fun pinCurrentColor(colorArgb: Int) {
        _presetColors.update { currentList ->
            val mutable = currentList.toMutableList()
            if (mutable.isNotEmpty()) {
                mutable[mutable.size - 1] = colorArgb // Заменяем последний закрепленный цвет
            } else {
                mutable.add(colorArgb)
            }
            mutable
        }
    }

    fun removePresetColor(index: Int) {
        _presetColors.update { currentList ->
            val mutable = currentList.toMutableList()
            if (index in mutable.indices && mutable.size > 1) {
                mutable.removeAt(index)
            }
            mutable
        }
    }

    fun selectColor(colorArgb: Int) {
        _activeBrush.update { it.copy(colorArgb = colorArgb) }
    }

    fun setSnappedBrushSize(rawSize: Float, step: Float = 4f) {
        val snapped = ((rawSize / step).roundToInt() * step).coerceIn(2f, 32f)
        _activeBrush.update { it.copy(baseSize = snapped) }
    }
    //endregion
}