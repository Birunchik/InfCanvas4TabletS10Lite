package com.infinitecanvas.board.presentation.screen.board.viewConrollers

import com.infinitecanvas.board.domain.model.ViewportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    fun updateCanvasSize(widthPx: Int, heightPx: Int) {
        if (widthPx > 0 && heightPx > 0) {
            canvasWidthPx = widthPx
            canvasHeightPx = heightPx
        }
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