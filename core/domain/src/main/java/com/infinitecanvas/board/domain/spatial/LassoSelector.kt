package com.infinitecanvas.board.domain.spatial

import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.WorldBounds
import kotlin.math.max
import kotlin.math.min

// Чистая геометрия, без знания о UI: на вход мировые координаты и quadtree.
// Полигон замыкается неявно — ребро из последней точки в первую.
object LassoSelector {

    private const val Y_BUCKETS = 16

    // Сложность ~O(кандидаты * 40): quadtree отсекает по AABB, дальше проверяются
    // только сэмплы, а не все точки длинного штриха.
    fun findSelectedStrokeIds(
        lassoPolygon: List<StrokePoint>,
        quadtree: Quadtree
    ): Set<Long> {
        val n = lassoPolygon.size
        if (n < 3) return emptySet()

        // 1. Плоские массивы координат + Bounding Box лассо
        val polyX = DoubleArray(n)
        val polyY = DoubleArray(n)
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        for (i in 0 until n) {
            val p = lassoPolygon[i]
            polyX[i] = p.x
            polyY[i] = p.y
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }

        val lassoBounds = WorldBounds(minX, minY, maxX, maxY)
        val candidates = ArrayList<Stroke>(64)
        quadtree.query(lassoBounds, candidates)
        if (candidates.isEmpty()) return emptySet()

        // 2. Предвычисление параметров рёбер и распределение по Y-бакетам
        val edgeMinY = DoubleArray(n)
        val edgeMaxY = DoubleArray(n)
        val edgeXi = DoubleArray(n)
        val edgeYi = DoubleArray(n)
        val edgeSlope = DoubleArray(n)

        // Y-бакеты: ребро попадает во все полосы, которые пересекает по Y, поэтому
        // для точки проверяются только рёбра её полосы, а не весь полигон.
        val height = (maxY - minY).coerceAtLeast(1e-6)
        val invBucketH = Y_BUCKETS / height
        val buckets = Array(Y_BUCKETS) { IntArray(0) }
        val bucketCounts = IntArray(Y_BUCKETS)

        var j = n - 1
        for (i in 0 until n) {
            val xi = polyX[i]
            val yi = polyY[i]
            val xj = polyX[j]
            val yj = polyY[j]

            edgeXi[i] = xi
            edgeYi[i] = yi
            edgeMinY[i] = min(yi, yj)
            edgeMaxY[i] = max(yi, yj)
            edgeSlope[i] = if (yi != yj) (xj - xi) / (yj - yi) else 0.0

            val bStart = ((edgeMinY[i] - minY) * invBucketH).toInt().coerceIn(0, Y_BUCKETS - 1)
            val bEnd = ((edgeMaxY[i] - minY) * invBucketH).toInt().coerceIn(0, Y_BUCKETS - 1)
            for (b in bStart..bEnd) {
                bucketCounts[b]++
            }
            j = i
        }

        for (b in 0 until Y_BUCKETS) {
            buckets[b] = IntArray(bucketCounts[b])
            bucketCounts[b] = 0
        }

        for (i in 0 until n) {
            val bStart = ((edgeMinY[i] - minY) * invBucketH).toInt().coerceIn(0, Y_BUCKETS - 1)
            val bEnd = ((edgeMaxY[i] - minY) * invBucketH).toInt().coerceIn(0, Y_BUCKETS - 1)
            for (b in bStart..bEnd) {
                val idx = bucketCounts[b]++
                buckets[b][idx] = i
            }
        }

        fun isInside(px: Double, py: Double): Boolean {
            if (px !in minX..maxX || py !in minY..maxY) return false
            val b = ((py - minY) * invBucketH).toInt().coerceIn(0, Y_BUCKETS - 1)
            val edgeIndices = buckets[b]
            var inside = false
            for (k in edgeIndices.indices) {
                val e = edgeIndices[k]
                // Полуинтервал [minY, maxY) обязателен: иначе вершина на луче
                // посчитается дважды и чётность inside сломается.
                if (py >= edgeMinY[e] && py < edgeMaxY[e]) {
                    val intersectX = edgeXi[e] + (py - edgeYi[e]) * edgeSlope[e]
                    if (px < intersectX) {
                        inside = !inside
                    }
                }
            }
            return inside
        }

        // 3. Проверка кандидатов с адаптивным шагом и ранним выходом
        val selectedIds = HashSet<Long>()
        for (i in candidates.indices) {
            val stroke = candidates[i]
            val pts = stroke.points
            val totalPts = pts.size
            if (totalPts == 0) continue

            // Адаптивный шаг: проверяем не более ~40 равномерно распределённых точек штриха
            val step = (totalPts / 40).coerceAtLeast(1)
            val sampledTotal = (totalPts + step - 1) / step
            val requiredHits = (sampledTotal + 1) / 2

            var insideCount = 0
            var checkedCount = 0

            var idx = 0
            while (idx < totalPts) {
                val pt = pts[idx]
                if (isInside(pt.x, pt.y)) {
                    insideCount++
                    if (insideCount >= requiredHits) {
                        selectedIds.add(stroke.id)
                        break // Ранний выход: 50% уже набрано
                    }
                }
                checkedCount++
                val remaining = sampledTotal - checkedCount
                if (insideCount + remaining < requiredHits) {
                    break // Ранний выход: набрать 50% уже математически невозможно
                }
                idx += step
            }
        }
        return selectedIds
    }
}