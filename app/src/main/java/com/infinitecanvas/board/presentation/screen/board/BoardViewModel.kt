package com.infinitecanvas.board.presentation.screen.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel экрана бесконечной доски.
 * Управляет положением камеры, списком векторных штрихов, выбором кисти и отменой действий.
 */
class BoardViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_OFFSET_X = "viewport_offset_x"
        private const val KEY_OFFSET_Y = "viewport_offset_y"
        private const val KEY_SCALE = "viewport_scale"
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

    private val _activeBrush = MutableStateFlow(
        Brush(
            type = BrushType.FOUNTAIN,
            colorArgb = 0xFF1A1C1E.toInt(),
            baseSize = 4f,
            opacity = 1f
        )
    )
    val activeBrush: StateFlow<Brush> = _activeBrush.asStateFlow()

    /**
     * Обновляет позицию и масштаб холста относительно фокуса жеста.
     */
    fun onTransform(
        panDeltaX: Float,
        panDeltaY: Float,
        zoomFactor: Float,
        centroidX: Float,
        centroidY: Float
    ) {
        _viewportState.update { current ->
            // Было: current.clampScale(...)
            // Стало (вызов метода companion object):
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

    /**
     * Сбрасывает масштаб на 100% и возвращает камеру в (0, 0).
     */
    fun resetViewport() {
        _viewportState.update {
            savedStateHandle[KEY_OFFSET_X] = 0.0
            savedStateHandle[KEY_OFFSET_Y] = 0.0
            savedStateHandle[KEY_SCALE] = 1.0
            ViewportState(offsetX = 0.0, offsetY = 0.0, scale = 1.0)
        }
    }

    /**
     * Добавляет завершённый штрих в историю доски.
     */
    fun onStrokeCommitted(stroke: Stroke) {
        _strokes.update { currentList -> currentList + stroke }
    }

    /**
     * Переключает активный пресет кисти.
     */
    fun selectBrushType(type: BrushType) {
        _activeBrush.update { it.copy(type = type) }
    }

    /**
     * Отменяет последний нарисованный штрих (Undo).
     */
    fun undoLastStroke() {
        _strokes.update { currentList ->
            if (currentList.isNotEmpty()) currentList.dropLast(1) else currentList
        }
    }
}