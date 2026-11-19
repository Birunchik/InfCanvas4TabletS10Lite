package com.infinitecanvas.board.domain.spatial

import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.domain.model.BrushDefaults.HIGHLIGHTER_WIDTH_MULTIPLIER
import kotlin.math.sqrt

/**
 * Пространственный индекс Quadtree для быстрого поиска штрихов.
 *
 * **Мутабельный**: [insert] и [remove] обновляют дерево in-place, сохраняя
 * инвариант через обратный индекс `nodeById`.
 *
 * **Not thread-safe.** Все мутации и запросы должны выполняться на одном потоке
 * (Main). Контроллеры досок вызывают мутации синхронно из UI-операций, а
 * Canvas-отрисовка читает дерево в том же Main-thread draw pass.
 *
 * Root bounds фиксированы на ±[DEFAULT_HALF_SIZE]. Штрихи, нарисованные
 * за пределами этого диапазона, попадают в корневой узел и будут возвращаться
 * `query` только при пересечении root bounds — типичный сценарий (viewport
 * zoom 0.15–8×) гарантированно укладывается внутрь.
 *
 * Штрих хранится в узле, который ПОЛНОСТЬЮ содержит его bounds; иначе остаётся
 * в родителе. query обязан обходить items родителя и детей, а не только листья.
 */
class Quadtree private constructor(
    private var root: Node
) {
    companion object {
        // Ограничения узла: 8 элементов до деления, глубина 20. При большем числе
        // совпадающих bounds потомки не создаются — узел хранит все штрихи списком.
        private const val MAX_ITEMS_PER_NODE = 8
        private const val MAX_DEPTH = 20
        private const val DEFAULT_HALF_SIZE = 1_000_000.0

        fun build(strokes: List<Stroke>): Quadtree {
            val emptyRoot = Node(
                WorldBounds(
                    -DEFAULT_HALF_SIZE,
                    -DEFAULT_HALF_SIZE,
                    DEFAULT_HALF_SIZE,
                    DEFAULT_HALF_SIZE
                ),
                0
            )
            val tree = Quadtree(emptyRoot)
            for (i in strokes.indices) {
                tree.insert(strokes[i])
            }
            return tree
        }
    }

    /**
     * Обратный индекс: stroke.id → узел, где физически лежит штрих.
     * Обновляется при каждой вставке и каждом перераспределении при subdivide.
     */
    private val nodeById = HashMap<Long, Node>()

    /** Вставляет штрих. Если штрих с таким id уже есть — старый orphan-узел не очищается. */
    fun insert(stroke: Stroke) {
        root.insert(stroke) { id, node -> nodeById[id] = node }
    }

    // Узлы после удаления не схлопываются: дерево остаётся раздробленным до
    // полной пересборки через build(); это приемлемо, пока удаления не массовые.
    /** Удаляет штрих по id. No-op, если id отсутствует. */
    fun remove(strokeId: Long) {
        val node = nodeById.remove(strokeId) ?: return
        node.removeItem(strokeId)
    }

    // Заполняет result, а не возвращает новый список: буфер переиспользуется
    // в draw-pass. Обход идёт сверху вниз, поэтому в результат попадают и items
    // родительских узлов — не только листьев.
    fun query(area: WorldBounds, result: MutableList<Stroke>) {
        result.clear()
        root.query(area, result)
    }

    /**
     * Возвращает ID штрихов, задетых круглой областью ластика в точке ([worldX], [worldY]).
     *
     * WARNING: возвращает view на [hitIdsBuffer] — валиден только до следующего
     * вызова [findHitStrokeIds] с тем же буфером. Потребитель должен обработать
     * результат синхронно, до возврата из текущего кадра.
     *
     * Запас поиска — половина максимальной ширины штриха (highlighter ×6): без него
     * широкий мазок не попадёт в выборку, хотя визуально касается ластика.
     */
    fun findHitStrokeIds(
        worldX: Double,
        worldY: Double,
        eraserRadius: Double,
        candidatesBuffer: MutableList<Stroke>,
        hitIdsBuffer: MutableSet<Long>
    ): Set<Long> {
        val maxHalfStrokePadding = 24.0 * HIGHLIGHTER_WIDTH_MULTIPLIER * 0.5
        val searchArea = WorldBounds.fromCenter(worldX, worldY, eraserRadius + maxHalfStrokePadding)

        candidatesBuffer.clear()
        root.query(searchArea, candidatesBuffer)

        hitIdsBuffer.clear()
        if (candidatesBuffer.isEmpty()) return emptySet()

        for (i in candidatesBuffer.indices) {
            val stroke = candidatesBuffer[i]
            if (isStrokeHitByCircle(stroke, worldX, worldY, eraserRadius)) {
                hitIdsBuffer.add(stroke.id)
            }
        }
        return hitIdsBuffer
    }

    // Ширины приближённо повторяют рендерер (с запасом). Проверяем расстояние до
    // отрезков, а не точек: между сэмплами штрих может быть длинным и прямым.
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
            BrushType.FOUNTAIN -> stroke.brush.baseSize * 0.55
            BrushType.BALLPOINT -> stroke.brush.baseSize * 0.5
            BrushType.PENCIL -> stroke.brush.baseSize * 0.6
            BrushType.MARKER -> stroke.brush.baseSize * 0.6
        }
        val hitThreshold = radius + visualHalfWidth

        if (pts.size == 1) {
            return hypotFast(pts[0].x - cx, pts[0].y - cy) <= hitThreshold
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
            return hypotFast(px - x1, py - y1)
        }
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0.0, 1.0)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypotFast(px - projX, py - projY)
    }

    private class Node(
        val bounds: WorldBounds,
        val depth: Int
    ) {
        private val items = ArrayList<Stroke>(MAX_ITEMS_PER_NODE)
        private var children: Array<Node>? = null

        /**
         * Вставляет штрих и обновляет [onRelocate] для самого штриха и всех
         * перемещённых при subdivide items. Возвращаемое значение не используется
         * вызывающим — актуальный узел доступен через [onRelocate].
         */
        fun insert(stroke: Stroke, onRelocate: (Long, Node) -> Unit) {
            val kids = children
            if (kids != null) {
                val childIndex = getChildIndex(stroke.bounds)
                if (childIndex != -1) {
                    kids[childIndex].insert(stroke, onRelocate)
                    return
                }
            }

            // В ребёнка пускаем только при полном contains; иначе штрих остаётся
            // здесь — иначе query потеряет его на границе квадрантов.
            items.add(stroke)
            onRelocate(stroke.id, this)

            if (children == null && items.size > MAX_ITEMS_PER_NODE && depth < MAX_DEPTH) {
                subdivide(onRelocate)
            }
        }

        fun removeItem(strokeId: Long) {
            items.removeAll { it.id == strokeId }
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

        // children публикуется до перекладывания: insert рекурсивно может снова
        // subdivide, и getChildIndex должен видеть уже созданных детей.
        private fun subdivide(onRelocate: (Long, Node) -> Unit) {
            val cx = bounds.centerX
            val cy = bounds.centerY
            val nextDepth = depth + 1
            val kids = arrayOf(
                Node(WorldBounds(bounds.minX, bounds.minY, cx, cy), nextDepth),
                Node(WorldBounds(cx, bounds.minY, bounds.maxX, cy), nextDepth),
                Node(WorldBounds(bounds.minX, cy, cx, bounds.maxY), nextDepth),
                Node(WorldBounds(cx, cy, bounds.maxX, bounds.maxY), nextDepth)
            )
            children = kids

            var i = 0
            while (i < items.size) {
                val item = items[i]
                val childIndex = getChildIndex(item.bounds)
                if (childIndex != -1) {
                    items.removeAt(i)
                    kids[childIndex].insert(item, onRelocate)
                } else {
                    i++
                }
            }
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

private fun hypotFast(dx: Double, dy: Double): Double = sqrt(dx * dx + dy * dy)