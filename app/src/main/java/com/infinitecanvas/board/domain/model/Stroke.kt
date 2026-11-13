package com.infinitecanvas.board.domain.model

import kotlin.math.max
import kotlin.math.min


//Отдельная точка штриха в мировых координатах доски.
data class StrokePoint(
    val x: Double,
    val y: Double,
    val pressure: Float
)


 //Ограничивающий прямоугольник штриха в мировых координатах (для viewport culling).
data class WorldBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {

    //Проверяет пересечение с видимой областью экрана.
    fun intersects(left: Double, top: Double, right: Double, bottom: Double): Boolean {
        return maxX >= left && minX <= right && maxY >= top && minY <= bottom
    }
}

/**
 Завершённый векторный штрих на бесконечном холсте.
 @property id Уникальный идентификатор штриха.
 @property points Упорядоченный список точек в мировых координатах.
 @property brush Параметры кисти, которой был нанесён штрих.
 @property bounds Предвычисленный bounding box с учётом толщины кисти.
 */
data class Stroke(
    val id: Long,
    val points: List<StrokePoint>,
    val brush: Brush,
    val bounds: WorldBounds
) {
    companion object {

        //Создаёт штрих и автоматически вычисляет его мировые границы для быстрого отсечения
        fun create(id: Long, points: List<StrokePoint>, brush: Brush): Stroke {
            val padding = (brush.baseSize * 2.5).coerceAtLeast(4.0)
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY

            for (p in points) {
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