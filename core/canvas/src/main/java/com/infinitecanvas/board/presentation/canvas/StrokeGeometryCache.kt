package com.infinitecanvas.board.presentation.canvas

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.WorldBounds

// 1500 записей — компромисс: при сильном зуме в кадр попадают тысячи штрихов,
// и маленький LRU заставлял бы перестраивать Path прямо в draw-фазе.
private const val MAX_GEOMETRY_CACHE_ENTRIES = 1500

internal class VariableStrokeSegment(
    val path: Path,
    val width: Float,
    val bounds: WorldBounds
)

internal class CachedStrokeGeometry(
    val bounds: WorldBounds,
    val path: Path,
    val color: Color,
    val isHighlighter: Boolean = false,
    val strokeWidth: Float = 0f,
    val singleDotCenter: Offset? = null,
    val singleDotRadius: Float = 0f,
    val variableSegments: List<VariableStrokeSegment> = emptyList(),
    val brushType: BrushType? = null
)

/**
 * Кэш геометрии по id штриха. Валидность проверяется по bounds, а не по id:
 * bounds меняются при драге и undo, и запись с несовпавшими bounds обязана пересобраться,
 * иначе на экране остался бы старый путь.
 */
internal class StrokeGeometryCache {
    private val cache = object : LinkedHashMap<Long, CachedStrokeGeometry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CachedStrokeGeometry>?): Boolean {
            return size > MAX_GEOMETRY_CACHE_ENTRIES
        }
    }

    fun getOrBuild(stroke: Stroke): CachedStrokeGeometry {
        val existing = cache[stroke.id]
        if (existing != null && existing.bounds == stroke.bounds) {
            return existing
        }

        val built = buildGeometry(stroke, Path())
        cache[stroke.id] = built
        return built
    }

    fun removeStrokes(ids: Set<Long>) {
        if (ids.isEmpty()) return
        for (id in ids) {
            cache.remove(id)
        }
    }

    fun shiftCachedStrokes(ids: Set<Long>, dx: Double, dy: Double) {
        if (ids.isEmpty() || (dx == 0.0 && dy == 0.0)) return
        // Мутируем Path in-place: translate дешевле пересборки, но смещение применяется ровно один раз
        // за коммит — повторный вызов с тем же dx сдвинет путь ещё раз.
        val offset = Offset(dx.toFloat(), dy.toFloat())

        for (id in ids) {
            val existing = cache[id] ?: continue
            val shiftedBounds = shiftBounds(existing.bounds, dx, dy)
            val shiftedDot = existing.singleDotCenter?.plus(offset)

            if (shiftedDot == null) {
                if (existing.variableSegments.isNotEmpty()) {
                    for (seg in existing.variableSegments) {
                        seg.path.translate(offset)
                    }
                } else {
                    existing.path.translate(offset)
                }
            }

            val shiftedSegments = if (existing.variableSegments.isEmpty()) {
                emptyList()
            } else {
                existing.variableSegments.map { seg ->
                    VariableStrokeSegment(
                        path = seg.path,
                        width = seg.width,
                        bounds = shiftBounds(seg.bounds, dx, dy)
                    )
                }
            }

            cache[id] = CachedStrokeGeometry(
                bounds = shiftedBounds,
                path = existing.path,
                color = existing.color,
                isHighlighter = existing.isHighlighter,
                strokeWidth = existing.strokeWidth,
                singleDotCenter = shiftedDot,
                singleDotRadius = existing.singleDotRadius,
                variableSegments = shiftedSegments,
                brushType = existing.brushType
            )
        }
    }

    private fun shiftBounds(b: WorldBounds, dx: Double, dy: Double) = WorldBounds(
        minX = b.minX + dx,
        minY = b.minY + dy,
        maxX = b.maxX + dx,
        maxY = b.maxY + dy
    )

    @VisibleForTesting
    internal fun cachedIdsSnapshot(): Set<Long> = cache.keys.toSet()
}
