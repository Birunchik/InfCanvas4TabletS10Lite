package com.infinitecanvas.board.domain.spatial

import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.WorldBounds

/**
 * Геометрический вычислитель выделения штрихов контуром лассо.
 * Работает без лишних аллокаций памяти с оптимизированным алгоритмом Ray Casting.
 */
object LassoSelector {

    /**
     * Возвращает ID штрихов, у которых значимая часть точек попала внутрь полигона [lassoPolygon].
     */
    fun findSelectedStrokeIds(
        lassoPolygon: List<StrokePoint>,
        quadtree: Quadtree
    ): Set<Long> {
        if (lassoPolygon.size < 3) return emptySet()

        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        for (i in lassoPolygon.indices) {
            val p = lassoPolygon[i]
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }

        val candidates = ArrayList<Stroke>(64)
        quadtree.query(WorldBounds(minX, minY, maxX, maxY), candidates)
        if (candidates.isEmpty()) return emptySet()

        val selectedIds = HashSet<Long>()
        for (i in candidates.indices) {
            val stroke = candidates[i]
            val pts = stroke.points
            if (pts.isEmpty()) continue

            var insideCount = 0

            // Оптимизация: если точек очень много, проверяем с шагом для ускорения,
            // сохраняя точность выделения
            val MAX_POINTS_TO_CHECK = 30
            val step = if (pts.size > MAX_POINTS_TO_CHECK) pts.size / MAX_POINTS_TO_CHECK else 1

            var checkedCount = 0
            for (j in pts.indices step step) {
                val pt = pts[j]
                checkedCount++
                if (pt.x in minX..maxX && pt.y in minY..maxY && isPointInPolygon(pt.x, pt.y, lassoPolygon)) {
                    insideCount++
                }
            }

            // Порог выделения: >= 50% проверенных точек штриха
            if (insideCount * 2 >= checkedCount) {
                selectedIds.add(stroke.id)
            }
        }
        return selectedIds
    }

    /**
     * Алгоритм трассировки луча (Ray Casting) для проверки попадания точки в произвольный полигон.
     */
    private fun isPointInPolygon(px: Double, py: Double, polygon: List<StrokePoint>): Boolean {
        var inside = false
        var j = polygon.size - 1 // Исправлен обрыв строки
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y

            val intersect = ((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / (yj - yi + 1e-12) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }
}