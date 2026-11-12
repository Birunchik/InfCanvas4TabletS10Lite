package com.infinitecanvas.board.presentation.screen.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.infinitecanvas.board.domain.model.ViewportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BoardViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_OFFSET_X = "viewport_offset_x"
        private const val KEY_OFFSET_Y = "viewport_offset_y"
        private const val KEY_SCALE = "viewport_scale"
    }

    // Восстанавливаем состояние вьюпорта из SavedStateHandle после пересоздания Activity
    private val _viewportState = MutableStateFlow(
        ViewportState(
            offsetX = savedStateHandle.get<Double>(KEY_OFFSET_X) ?: 0.0,
            offsetY = savedStateHandle.get<Double>(KEY_OFFSET_Y) ?: 0.0,
            scale = savedStateHandle.get<Double>(KEY_SCALE) ?: 1.0
        )
    )
    val viewportState: StateFlow<ViewportState> = _viewportState.asStateFlow()

    /**
     * Применяет жест панорамирования и масштабирования относительно точки фокуса (centroid).
     * Точка на холсте под пальцами остается неподвижной во время зума.
     */
    fun onTransform(panDeltaX: Float, panDeltaY: Float, zoomFactor: Float, centroidX: Float, centroidY: Float) {
        _viewportState.update { current ->
            val newScale = ViewportState.clampScale(current.scale * zoomFactor)
            val actualZoom = newScale / current.scale

            // Вычисляем новое смещение с учетом фокуса масштабирования и сдвига
            val newOffsetX = centroidX - (centroidX - current.offsetX) * actualZoom + panDeltaX
            val newOffsetY = centroidY - (centroidY - current.offsetY) * actualZoom + panDeltaY

            // Сохраняем в SavedStateHandle для переживания поворота экрана
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
     * Сброс камеры в центр координат (100% масштаб).
     */
    fun resetViewport() {
        _viewportState.update {
            savedStateHandle[KEY_OFFSET_X] = 0.0
            savedStateHandle[KEY_OFFSET_Y] = 0.0
            savedStateHandle[KEY_SCALE] = 1.0
            ViewportState(offsetX = 0.0, offsetY = 0.0, scale = 1.0)
        }
    }
}