package com.infinitecanvas.board.presentation.canvas.gestures

import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerType

internal class ViewportGestureHandler(
    private val isZoomLocked: () -> Boolean,
    private val onTransform: (panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) -> Unit
) {
    suspend fun AwaitPointerEventScope.handleViewportPanZoom() {
        do {
            val event = awaitPointerEvent()
            val stylusAppeared = event.changes.any {
                it.type == PointerType.Stylus || it.type == PointerType.Eraser
            }
            if (stylusAppeared) break

            val rawZoom = event.calculateZoom()
            val pan = event.calculatePan()
            val centroid = event.calculateCentroid(useCurrent = false)
            val zoom = if (isZoomLocked()) 1f else rawZoom

            if (centroid != Offset.Unspecified && (zoom != 1f || pan != Offset.Zero)) {
                onTransform(pan.x, pan.y, zoom, centroid.x, centroid.y)
                // consume() обязателен: иначе родительские обработчики (например, скролл контейнера)
                // обработают тот же жест повторно.
                event.changes.forEach { it.consume() }
            }
            // Цикл до отпускания последнего пальца: добавление второго пальца меняет центроид,
            // и жест продолжается как pinch без перезапуска детектора.
        } while (event.changes.any { it.pressed })
    }
}
