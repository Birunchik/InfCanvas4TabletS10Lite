package com.infinitecanvas.board.presentation.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.WorldBounds
import kotlin.math.hypot

class CachedStrokeGeometry(
    val bounds: WorldBounds,
    val path: Path,
    val color: Color,
    val isFilledPolygon: Boolean,
    val strokeWidth: Float,
    val singleDotCenter: Offset? = null,
    val singleDotRadius: Float = 0f
)

class StrokeGeometryCache {
    private val cache = HashMap<Long, CachedStrokeGeometry>(256)

    fun getOrBuild(stroke: Stroke): CachedStrokeGeometry {
        val existing = cache[stroke.id]
        if (existing != null && existing.bounds == stroke.bounds) {
            return existing
        }

        if (cache.size > 2000) {
            cache.clear()
        }

        val built = buildGeometry(stroke, Path())
        cache[stroke.id] = built
        return built
    }
}

fun buildGeometry(
    stroke: Stroke,
    targetPath: Path
): CachedStrokeGeometry {
    val points = stroke.points
    val brush = stroke.brush
    val baseColor = Color(brush.colorArgb)
    val effectiveAlpha = when (brush.type) {
        BrushType.PENCIL -> (brush.opacity * 0.45f).coerceIn(0.1f, 1f)
        else -> brush.opacity
    }
    val strokeColor = baseColor.copy(alpha = effectiveAlpha)

    if (points.size == 1) {
        val p = points[0]
        return CachedStrokeGeometry(
            bounds = stroke.bounds,
            path = targetPath,
            color = strokeColor,
            isFilledPolygon = true,
            strokeWidth = 0f,
            singleDotCenter = Offset(p.x.toFloat(), p.y.toFloat()),
            singleDotRadius = calculateWidthForBrush(brush, p.pressure) * 0.5f
        )
    }

    targetPath.reset()
    return when (brush.type) {
        BrushType.BALLPOINT, BrushType.PENCIL -> {
            targetPath.moveTo(points[0].x.toFloat(), points[0].y.toFloat())
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = ((prev.x + curr.x) * 0.5).toFloat()
                val midY = ((prev.y + curr.y) * 0.5).toFloat()
                targetPath.quadraticTo(prev.x.toFloat(), prev.y.toFloat(), midX, midY)
            }
            val last = points.last()
            targetPath.lineTo(last.x.toFloat(), last.y.toFloat())

            val width = if (brush.type == BrushType.PENCIL) {
                brush.baseSize * 0.75f
            } else {
                brush.baseSize
            }

            CachedStrokeGeometry(
                bounds = stroke.bounds,
                path = targetPath,
                color = strokeColor,
                isFilledPolygon = false,
                strokeWidth = width
            )
        }

        BrushType.FOUNTAIN -> {
            buildVariableWidthOutline(points, brush, targetPath)
            CachedStrokeGeometry(
                bounds = stroke.bounds,
                path = targetPath,
                color = strokeColor,
                isFilledPolygon = true,
                strokeWidth = 0f
            )
        }
    }
}

private fun buildVariableWidthOutline(
    points: List<StrokePoint>,
    brush: Brush,
    outPath: Path
) {
    val n = points.size
    if (n < 2) return

    for (i in 0 until n) {
        val curr = points[i]
        val prev = if (i > 0) points[i - 1] else curr
        val next = if (i < n - 1) points[i + 1] else curr

        var dx = (next.x - prev.x).toFloat()
        var dy = (next.y - prev.y).toFloat()
        val len = hypot(dx, dy)
        if (len > 1e-4f) {
            dx /= len
            dy /= len
        } else {
            dx = 1f
            dy = 0f
        }

        val halfW = calculateWidthForBrush(brush, curr.pressure) * 0.5f
        val nx = -dy * halfW
        val ny = dx * halfW

        val lx = curr.x.toFloat() + nx
        val ly = curr.y.toFloat() + ny
        if (i == 0) outPath.moveTo(lx, ly) else outPath.lineTo(lx, ly)
    }

    for (i in n - 1 downTo 0) {
        val curr = points[i]
        val prev = if (i > 0) points[i - 1] else curr
        val next = if (i < n - 1) points[i + 1] else curr

        var dx = (next.x - prev.x).toFloat()
        var dy = (next.y - prev.y).toFloat()
        val len = hypot(dx, dy)
        if (len > 1e-4f) {
            dx /= len
            dy /= len
        } else {
            dx = 1f
            dy = 0f
        }

        val halfW = calculateWidthForBrush(brush, curr.pressure) * 0.5f
        val nx = -dy * halfW
        val ny = dx * halfW

        val rx = curr.x.toFloat() - nx
        val ry = curr.y.toFloat() - ny
        outPath.lineTo(rx, ry)
    }
    outPath.close()
}

fun calculateWidthForBrush(brush: Brush, pressure: Float): Float {
    return when (brush.type) {
        BrushType.BALLPOINT -> brush.baseSize
        BrushType.FOUNTAIN -> brush.baseSize * (0.4f + pressure * 1.2f)
        BrushType.PENCIL -> brush.baseSize * (0.45f + pressure * 0.55f)
    }.coerceAtLeast(0.5f)
}