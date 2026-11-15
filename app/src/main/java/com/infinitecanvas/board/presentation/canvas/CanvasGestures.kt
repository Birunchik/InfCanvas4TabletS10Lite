package com.infinitecanvas.board.presentation.canvas

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.spatial.Quadtree
import kotlin.math.hypot


// Утилиты фильтрации точек
internal fun appendFilteredPoints(
    change: PointerInputChange,
    viewport: ViewportState,
    target: MutableList<StrokePoint>
): Boolean {
    var added = false
    val minDistWorld = 1.2 / viewport.scale.coerceAtLeast(0.25)
    val basePressure = change.pressure.coerceIn(0.05f, 1f)

    for (historical in change.historical) {
        val wx = viewport.screenToWorldX(historical.position.x)
        val wy = viewport.screenToWorldY(historical.position.y)
        if (shouldAddPoint(target, wx, wy, minDistWorld)) {
            target.add(StrokePoint(wx, wy, basePressure))
            added = true
        }
    }

    val wx = viewport.screenToWorldX(change.position.x)
    val wy = viewport.screenToWorldY(change.position.y)
    if (shouldAddPoint(target, wx, wy, minDistWorld)) {
        target.add(StrokePoint(wx, wy, basePressure))
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