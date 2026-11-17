package com.infinitecanvas.board.presentation.canvas.gestures

import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.spatial.Quadtree
import kotlin.math.hypot

internal fun appendFilteredPoints(
    change: PointerInputChange,
    viewport: ViewportState,
    target: MutableList<StrokePoint>,
    minStepPx: Double = 2.0,
    incrementalPath: Path? = null
): Boolean {
    var added = false
    val minDistWorld = (minStepPx / viewport.scale.coerceAtLeast(0.25)).coerceAtMost(2.5)
    val basePressure = change.pressure.coerceIn(0.05f, 1f)

    for (historical in change.historical) {
        val wx = viewport.screenToWorldX(historical.position.x)
        val wy = viewport.screenToWorldY(historical.position.y)
        if (shouldAddPoint(target, wx, wy, minDistWorld)) {
            if (incrementalPath != null) {
                if (target.isEmpty()) incrementalPath.moveTo(wx.toFloat(), wy.toFloat())
                else incrementalPath.lineTo(wx.toFloat(), wy.toFloat())
            }
            target.add(StrokePoint(wx, wy, basePressure, historical.uptimeMillis))
            added = true
        }
    }

    val wx = viewport.screenToWorldX(change.position.x)
    val wy = viewport.screenToWorldY(change.position.y)
    if (shouldAddPoint(target, wx, wy, minDistWorld)) {
        if (incrementalPath != null) {
            if (target.isEmpty()) incrementalPath.moveTo(wx.toFloat(), wy.toFloat())
            else incrementalPath.lineTo(wx.toFloat(), wy.toFloat())
        }
        target.add(StrokePoint(wx, wy, basePressure, change.uptimeMillis))
        added = true
    }
    return added
}

private fun shouldAddPoint(
    target: List<StrokePoint>,
    wx: Double,
    wy: Double,
    minDist: Double
): Boolean {
    if (target.isEmpty()) return true
    val last = target.last()
    return hypot(wx - last.x, wy - last.y) >= minDist
}

internal fun eraseAtScreenPosition(
    screenPos: Offset,
    viewport: ViewportState,
    quadtree: Quadtree,
    onErase: (Set<Long>) -> Unit
) {
    val worldX = viewport.screenToWorldX(screenPos.x)
    val worldY = viewport.screenToWorldY(screenPos.y)
    val worldEraserRadius = 18.0 / viewport.scale.coerceAtLeast(0.15)
    val hitIds = quadtree.findHitStrokeIds(worldX, worldY, worldEraserRadius)
    if (hitIds.isNotEmpty()) {
        onErase(hitIds)
    }
}

internal suspend fun AwaitPointerEventScope.handleViewportPanZoom(
    isZoomLocked: () -> Boolean,
    onTransform: (panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) -> Unit
) {
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
            event.changes.forEach { it.consume() }
        }
    } while (event.changes.any { it.pressed })
}