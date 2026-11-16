package com.infinitecanvas.board.domain.model

import kotlin.math.max
import kotlin.math.min

/**
 * Отдельная точка штриха в мировых координатах доски.
 *
 * @property x Мировая координата X.
 * @property y Мировая координата Y.
 * @property pressure Нормализованная сила нажатия стилуса (0.0f..1.0f).
 */
data class StrokePoint(
    val x: Double,
    val y: Double,
    val pressure: Float
)

/**
 * Ограничивающий прямоугольник в мировых координатах (для Quadtree и viewport culling).
 */
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

    /**
     * Проверяет пересечение с видимой областью или другим прямоугольником.
     */
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

 //Завершённый векторный штрих на бесконечном холсте.
data class Stroke(
    val id: Long,
    val points: List<StrokePoint>,
    val brush: Brush,
    val bounds: WorldBounds
) {

    //Возвращает копию штриха, сдвинутую на ([dx], [dy]) в мировых координатах,
    fun translated(dx: Double, dy: Double): Stroke {
        if (dx == 0.0 && dy == 0.0) return this
        val shiftedPoints = points.map { pt ->
            pt.copy(x = pt.x + dx, y = pt.y + dy)
        }
        return create(
            id = id,
            points = shiftedPoints,
            brush = brush
        )
    }

    companion object {
        /**
         * Создаёт штрих и автоматически вычисляет его мировые границы.
         */
        fun create(id: Long, points: List<StrokePoint>, brush: Brush): Stroke {
            val padding = (brush.baseSize * 2.5).coerceAtLeast(4.0)
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY

            for ((x, y) in points) {
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
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