package com.infinitecanvas.board.presentation.screen.board.viewControllers

import com.infinitecanvas.board.domain.model.ViewportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Размер canvas живёт здесь, а не в ViewportState: он не сохраняется в доску,
// но нужен конвертациям screen↔world для pinned-картинок.
class BoardViewportController(
    private val onViewportChanged: () -> Unit
) {
    var canvasWidthPx: Int = 1280
        private set
    var canvasHeightPx: Int = 800
        private set

    private val _viewportState = MutableStateFlow(ViewportState())
    val viewportState: StateFlow<ViewportState> = _viewportState.asStateFlow()

    private val _isZoomLocked = MutableStateFlow(false)
    val isZoomLocked: StateFlow<Boolean> = _isZoomLocked.asStateFlow()

    fun setViewport(state: ViewportState) {
        _viewportState.value = state
    }

    // Нулевые размеры игнорируем: Compose присылает 0 на первом layout или при
    // сворачивании, а делить на такой размер в конвертациях нельзя.
    fun updateCanvasSize(widthPx: Int, heightPx: Int) {
        if (widthPx > 0 && heightPx > 0) {
            canvasWidthPx = widthPx
            canvasHeightPx = heightPx
        }
    }

    // Центроид — точка, которая остаётся под пальцами: offset пересчитывается
    // относительно неё, поэтому зум не «уезжает» в угол экрана.
    fun onTransform(
        panX: Float,
        panY: Float,
        zoom: Float,
        centroidX: Float,
        centroidY: Float
    ) {
        _viewportState.update { state ->
            // Lock отключает только масштаб: pan применяется всегда, иначе при заблокированном
            // зуме доску нельзя было бы двигать.
            val effectiveZoom = if (_isZoomLocked.value) 1.0 else zoom.toDouble()
            // Диапазон 0.15..8.0 задан здесь; ViewportState.MAX_SCALE = 1.5 для жеста
            // не применяется, иначе зум упирался бы в 150 %.
            val newScale = (state.scale * effectiveZoom).coerceIn(0.15, 8.0)
            // Делим на state.scale, а не берём zoom: на границах клампа фактический масштаб
            // меньше жеста, и offset должен следовать за фактическим, иначе картинка «поплывёт».
            val actualZoom = newScale / state.scale

            val newOffsetX = centroidX - (centroidX - state.offsetX) * actualZoom + panX
            val newOffsetY = centroidY - (centroidY - state.offsetY) * actualZoom + panY

            state.copy(
                offsetX = newOffsetX,
                offsetY = newOffsetY,
                scale = newScale
            )
        }
        onViewportChanged()
    }

    fun toggleZoomLock() {
        _isZoomLocked.update { !it }
    }

    fun resetViewport() {
        _viewportState.value = ViewportState()
        onViewportChanged()
    }
}