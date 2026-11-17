package com.infinitecanvas.board.domain.spatial

import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.presentation.canvas.HIGHLIGHTER_WIDTH_MULTIPLIER
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Пространственный индекс Quadtree для быстрого поиска штрихов в видимой области
 * и мгновенного определения пересечений с ластиком.
 */
class Quadtree private constructor(
    private val root: Node
) {
    companion object {
        private const val MAX_ITEMS_PER_NODE = 8
        private const val MAX_DEPTH = 8
        private const val DEFAULT_HALF_SIZE = 4096.0

        fun build(strokes: List<Stroke>): Quadtree {
            if (strokes.isEmpty()) {
                val emptyBounds = WorldBounds(
                    -DEFAULT_HALF_SIZE,
                    -DEFAULT_HALF_SIZE,
                    DEFAULT_HALF_SIZE,
                    DEFAULT_HALF_SIZE
                )
                return Quadtree(Node(emptyBounds, 0))
            }

            var minX = -DEFAULT_HALF_SIZE
            var minY = -DEFAULT_HALF_SIZE
            var maxX = DEFAULT_HALF_SIZE
            var maxY = DEFAULT_HALF_SIZE

            for (i in strokes.indices) {
                val b = strokes[i].bounds
                minX = min(minX, b.minX - 64.0)
                minY = min(minY, b.minY - 64.0)
                maxX = max(maxX, b.maxX + 64.0)
                maxY = max(maxY, b.maxY + 64.0)
            }

            val rootNode = Node(WorldBounds(minX, minY, maxX, maxY), 0)
            for (i in strokes.indices) {
                rootNode.insert(strokes[i])
            }
            return Quadtree(rootNode)
        }
    }

    fun query(area: WorldBounds, result: MutableList<Stroke>) {
        result.clear()
        root.query(area, result)
    }

    /**
     * Возвращает ID штрихов, задетых круглой областью ластика в точке ([worldX], [worldY]).
     */
    fun findHitStrokeIds(worldX: Double, worldY: Double, eraserRadius: Double): Set<Long> {
        // Расширяем область поиска на максимальную полуширину маркера, чтобы цеплять его за края
        val maxHalfStrokePadding = 24.0 * HIGHLIGHTER_WIDTH_MULTIPLIER * 0.5
        val searchArea = WorldBounds.fromCenter(worldX, worldY, eraserRadius + maxHalfStrokePadding)
        val candidates = ArrayList<Stroke>(16)
        root.query(searchArea, candidates)

        if (candidates.isEmpty()) return emptySet()

        val hitIds = HashSet<Long>()
        for (i in candidates.indices) {
            val stroke = candidates[i]
            if (isStrokeHitByCircle(stroke, worldX, worldY, eraserRadius)) {
                hitIds.add(stroke.id)
            }
        }
        return hitIds
    }

    private fun isStrokeHitByCircle(
        stroke: Stroke,
        cx: Double,
        cy: Double,
        radius: Double
    ): Boolean {
        val pts = stroke.points
        if (pts.isEmpty()) return false

        val visualHalfWidth = when (stroke.brush.type) {
            BrushType.HIGHLIGHTER -> stroke.brush.baseSize * HIGHLIGHTER_WIDTH_MULTIPLIER * 0.5
            BrushType.FOUNTAIN -> stroke.brush.baseSize * 1.1 * 0.5
            BrushType.BALLPOINT -> stroke.brush.baseSize * 0.5
        }
        val hitThreshold = radius + visualHalfWidth

        if (pts.size == 1) {
            return hypot(pts[0].x - cx, pts[0].y - cy) <= hitThreshold
        }

        for (i in 1 until pts.size) {
            val p0 = pts[i - 1]
            val p1 = pts[i]
            if (distancePointToSegment(cx, cy, p0.x, p0.y, p1.x, p1.y) <= hitThreshold) {
                return true
            }
        }
        return false
    }

    private fun distancePointToSegment(
        px: Double,
        py: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double
    ): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-9) {
            return hypot(px - x1, py - y1)
        }
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0.0, 1.0)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot(px - projX, py - projY)
    }

    private class Node(
        val bounds: WorldBounds,
        val depth: Int
    ) {
        private val items = ArrayList<Stroke>(MAX_ITEMS_PER_NODE)
        private var children: Array<Node>? = null

        fun insert(stroke: Stroke) {
            val kids = children
            if (kids != null) {
                val childIndex = getChildIndex(stroke.bounds)
                if (childIndex != -1) {
                    kids[childIndex].insert(stroke)
                    return
                }
            }

            items.add(stroke)

            if (children == null && items.size > MAX_ITEMS_PER_NODE && depth < MAX_DEPTH) {
                subdivide()
                var i = 0
                while (i < items.size) {
                    val idx = getChildIndex(items[i].bounds)
                    if (idx != -1) {
                        val moved = items.removeAt(i)
                        children!![idx].insert(moved)
                    } else {
                        i++
                    }
                }
            }
        }

        fun query(area: WorldBounds, result: MutableList<Stroke>) {
            if (!bounds.intersects(area)) return

            for (i in items.indices) {
                val item = items[i]
                if (item.bounds.intersects(area)) {
                    result.add(item)
                }
            }

            val kids = children ?: return
            for (i in kids.indices) {
                kids[i].query(area, result)
            }
        }

        private fun subdivide() {
            val cx = bounds.centerX
            val cy = bounds.centerY
            val nextDepth = depth + 1
            children = arrayOf(
                Node(WorldBounds(bounds.minX, bounds.minY, cx, cy), nextDepth),
                Node(WorldBounds(cx, bounds.minY, bounds.maxX, cy), nextDepth),
                Node(WorldBounds(bounds.minX, cy, cx, bounds.maxY), nextDepth),
                Node(WorldBounds(cx, cy, bounds.maxX, bounds.maxY), nextDepth)
            )
        }

        private fun getChildIndex(itemBounds: WorldBounds): Int {
            val kids = children ?: return -1
            for (i in kids.indices) {
                if (kids[i].bounds.contains(itemBounds)) {
                    return i
                }
            }
            return -1
        }
    }
}