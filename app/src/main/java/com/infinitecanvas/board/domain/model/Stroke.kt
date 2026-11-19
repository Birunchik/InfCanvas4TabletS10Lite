package com.infinitecanvas.board.domain.model

import kotlin.math.max
import kotlin.math.min

data class StrokePoint(
    val x: Double,
    val y: Double,
    val pressure: Float,
    val timestampMs: Long = 0L
)

data class WorldBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    val centerX: Double get() = (minX + maxX) * 0.5
    val centerY: Double get() = (minY + maxY) * 0.5

    fun intersects(left: Double, top: Double, right: Double, bottom: Double): Boolean {
        return maxX >= left && minX <= right && maxY >= top && minY <= bottom
    }

    fun intersects(other: WorldBounds): Boolean {
        return intersects(other.minX, other.minY, other.maxX, other.maxY)
    }

    fun contains(other: WorldBounds): Boolean {
        return other.minX >= minX && other.maxX <= maxX &&
                other.minY >= minY && other.maxY <= maxY
    }

    companion object {
        fun fromCenter(cx: Double, cy: Double, radius: Double): WorldBounds {
            return WorldBounds(
                minX = cx - radius,
                minY = cy - radius,
                maxX = cx + radius,
                maxY = cy + radius
            )
        }
    }
}

data class Stroke(
    val id: Long,
    val points: List<StrokePoint>,
    val brush: Brush,
    val bounds: WorldBounds
) {
    fun translated(dx: Double, dy: Double): Stroke {
        if (dx == 0.0 && dy == 0.0) return this
        val shiftedPoints = points.map { pt ->
            pt.copy(x = pt.x + dx, y = pt.y + dy)
        }
        return copy(
            points = shiftedPoints,
            bounds = WorldBounds(
                minX = bounds.minX + dx,
                minY = bounds.minY + dy,
                maxX = bounds.maxX + dx,
                maxY = bounds.maxY + dy
            )
        )
    }

    companion object {
        fun create(id: Long, points: List<StrokePoint>, brush: Brush): Stroke {
            val visualWidth = when (brush.type) {
                BrushType.HIGHLIGHTER -> brush.baseSize * 6.0
                BrushType.FOUNTAIN -> brush.baseSize * 1.6
                BrushType.PENCIL -> brush.baseSize * 1.2
                BrushType.MARKER -> brush.baseSize * 1.2
                BrushType.BALLPOINT -> brush.baseSize * 1.05
            }
            val padding = (visualWidth * 0.65).coerceAtLeast(4.0)

            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY

            for (i in points.indices) {
                val p = points[i]
                minX = min(minX, p.x)
                minY = min(minY, p.y)
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }

            return Stroke(
                id = id,
                points = points,
                brush = brush,
                bounds = WorldBounds(
                    minX = minX - padding,
                    minY = minY - padding,
                    maxX = maxX + padding,
                    maxY = maxY + padding
                )
            )
        }
    }
}