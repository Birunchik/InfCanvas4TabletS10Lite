package com.infinitecanvas.board.presentation.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.WorldBounds
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

const val HIGHLIGHTER_WIDTH_MULTIPLIER = 6.0f
const val HIGHLIGHTER_CHISEL_ANGLE_DEG = -30f

class VariableStrokeSegment(
    val path: Path,
    val width: Float,
    val bounds: WorldBounds
)

class CachedStrokeGeometry(
    val bounds: WorldBounds,
    val path: Path,
    val color: Color,
    val isHighlighter: Boolean = false,
    val strokeWidth: Float = 0f,
    val singleDotCenter: Offset? = null,
    val singleDotRadius: Float = 0f,
    val variableSegments: List<VariableStrokeSegment> = emptyList()
)

class StrokeGeometryCache {
    private val cache = HashMap<Long, CachedStrokeGeometry>(256)

    fun getOrBuild(stroke: Stroke): CachedStrokeGeometry {
        val existing = cache[stroke.id]
        if (existing != null && existing.bounds == stroke.bounds) {
            return existing
        }

        if (cache.size > 1500) {
            cache.clear()
        }

        val built = buildGeometry(stroke, Path())
        cache[stroke.id] = built
        return built
    }

    fun shiftCachedStrokes(ids: Set<Long>, dx: Double, dy: Double) {
        if (ids.isEmpty() || (dx == 0.0 && dy == 0.0)) return
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
                variableSegments = shiftedSegments
            )
        }
    }

    private fun shiftBounds(b: WorldBounds, dx: Double, dy: Double) = WorldBounds(
        minX = b.minX + dx,
        minY = b.minY + dy,
        maxX = b.maxX + dx,
        maxY = b.maxY + dy
    )
}

fun buildGeometry(
    stroke: Stroke,
    targetPath: Path
): CachedStrokeGeometry {
    val rawPoints = stroke.points
    val brush = stroke.brush
    val baseColor = Color(brush.colorArgb)
    val isHighlighter = brush.type == BrushType.HIGHLIGHTER
    val effectiveAlpha = if (isHighlighter) {
        brush.opacity.coerceIn(0.35f, 0.45f)
    } else {
        brush.opacity
    }
    val strokeColor = baseColor.copy(alpha = effectiveAlpha)

    val effectiveWidth = calculateWidthForBrush(brush, pressure = 0.5f, speedPxPerMs = 0f)
    val effectiveBounds = if (isHighlighter) {
        val extraPad = (effectiveWidth * 0.85)
        WorldBounds(
            minX = stroke.bounds.minX - extraPad,
            minY = stroke.bounds.minY - extraPad,
            maxX = stroke.bounds.maxX + extraPad,
            maxY = stroke.bounds.maxY + extraPad
        )
    } else {
        stroke.bounds
    }

    targetPath.reset()

    if (rawPoints.size == 1) {
        val p = rawPoints[0]
        if (isHighlighter) {
            targetPath.moveTo(p.x.toFloat(), p.y.toFloat())
            targetPath.lineTo(p.x.toFloat() + 0.5f, p.y.toFloat())
            applyChiselForwardTransform(targetPath)
            return CachedStrokeGeometry(
                bounds = effectiveBounds,
                path = targetPath,
                color = strokeColor,
                isHighlighter = true,
                strokeWidth = effectiveWidth
            )
        }
        return CachedStrokeGeometry(
            bounds = effectiveBounds,
            path = targetPath,
            color = strokeColor,
            isHighlighter = false,
            singleDotCenter = Offset(p.x.toFloat(), p.y.toFloat()),
            singleDotRadius = calculateWidthForBrush(brush, p.pressure, speedPxPerMs = 0f) * 0.5f
        )
    }

    val points = smoothSpineCoordinates(rawPoints)

    return when (brush.type) {
        BrushType.HIGHLIGHTER -> {
            buildSmoothQuadraticPath(points, targetPath)
            applyChiselForwardTransform(targetPath)
            CachedStrokeGeometry(
                bounds = effectiveBounds,
                path = targetPath,
                color = strokeColor,
                isHighlighter = true,
                strokeWidth = effectiveWidth
            )
        }

        BrushType.BALLPOINT -> {
            buildSmoothQuadraticPath(points, targetPath)
            val avgPressure = points.fold(0f) { acc, pt -> acc + pt.pressure } / points.size
            val width = calculateWidthForBrush(brush, avgPressure, 0f)
            CachedStrokeGeometry(
                bounds = effectiveBounds,
                path = targetPath,
                color = strokeColor,
                isHighlighter = false,
                strokeWidth = width
            )
        }

        BrushType.FOUNTAIN -> {
            val segments = buildQuantizedFountainSegments(points, brush)
            CachedStrokeGeometry(
                bounds = effectiveBounds,
                path = targetPath,
                color = strokeColor,
                isHighlighter = false,
                strokeWidth = 0f,
                variableSegments = segments
            )
        }
    }
}

private fun buildSmoothQuadraticPath(points: List<StrokePoint>, outPath: Path) {
    outPath.moveTo(points[0].x.toFloat(), points[0].y.toFloat())
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val midX = ((prev.x + curr.x) * 0.5).toFloat()
        val midY = ((prev.y + curr.y) * 0.5).toFloat()
        outPath.quadraticTo(prev.x.toFloat(), prev.y.toFloat(), midX, midY)
    }
    val last = points.last()
    outPath.lineTo(last.x.toFloat(), last.y.toFloat())
}

private fun smoothSpineCoordinates(raw: List<StrokePoint>): List<StrokePoint> {
    if (raw.size < 3) return raw

    var current = raw
    repeat(2) {
        val n = current.size
        val nextList = ArrayList<StrokePoint>(n)
        nextList.add(current[0])
        for (i in 1 until n - 1) {
            val prev = current[i - 1]
            val curr = current[i]
            val next = current[i + 1]
            nextList.add(
                StrokePoint(
                    x = prev.x * 0.25 + curr.x * 0.5 + next.x * 0.25,
                    y = prev.y * 0.25 + curr.y * 0.5 + next.y * 0.25,
                    pressure = curr.pressure,
                    timestampMs = curr.timestampMs
                )
            )
        }
        nextList.add(current[n - 1])
        current = nextList
    }
    return current
}

private fun buildQuantizedFountainSegments(
    points: List<StrokePoint>,
    brush: Brush
): List<VariableStrokeSegment> {
    val n = points.size
    if (n < 2) return emptyList()

    val widths = computeSmoothedWidths(points, brush)
    val result = ArrayList<VariableStrokeSegment>(min(n, 32))

    val widthTolerance = max(0.32f, brush.baseSize * 0.08f)
    val minPointsPerSegment = 4

    var segStartIdx = 0
    var currentBucketWidth = widths[0]

    for (i in 1 until n) {
        val w = widths[i]
        val isLast = (i == n - 1)
        val pointsInSeg = i - segStartIdx

        if ((pointsInSeg >= minPointsPerSegment && abs(w - currentBucketWidth) > widthTolerance) || isLast) {
            val avgWidth = (currentBucketWidth + w) * 0.5f
            result.add(createSmoothSegmentFromRange(points, segStartIdx, i, avgWidth))
            segStartIdx = i
            currentBucketWidth = w
        }
    }

    return result
}

private fun createSmoothSegmentFromRange(
    points: List<StrokePoint>,
    startIdx: Int,
    endIdx: Int,
    width: Float
): VariableStrokeSegment {
    val path = Path()
    val p0 = points[startIdx]
    path.moveTo(p0.x.toFloat(), p0.y.toFloat())

    var minX = p0.x
    var minY = p0.y
    var maxX = p0.x
    var maxY = p0.y

    for (i in (startIdx + 1)..endIdx) {
        val prev = points[i - 1]
        val curr = points[i]
        minX = min(minX, curr.x)
        minY = min(minY, curr.y)
        maxX = max(maxX, curr.x)
        maxY = max(maxY, curr.y)

        if (i == endIdx) {
            path.lineTo(curr.x.toFloat(), curr.y.toFloat())
        } else {
            val midX = ((prev.x + curr.x) * 0.5).toFloat()
            val midY = ((prev.y + curr.y) * 0.5).toFloat()
            path.quadraticTo(prev.x.toFloat(), prev.y.toFloat(), midX, midY)
        }
    }

    val pad = (width * 0.65).coerceAtLeast(2.0)
    return VariableStrokeSegment(
        path = path,
        width = width,
        bounds = WorldBounds(minX - pad, minY - pad, maxX + pad, maxY + pad)
    )
}

private fun computeSmoothedWidths(
    points: List<StrokePoint>,
    brush: Brush
): FloatArray {
    val n = points.size
    val rawWidths = FloatArray(n)

    var smoothedPressure = points[0].pressure.coerceIn(0.05f, 1f)
    var smoothedSpeed = 0f
    var windowStartIdx = 0
    var windowDist = 0f

    for (i in 0 until n) {
        val curr = points[i]
        val rawPressure = curr.pressure.coerceIn(0.05f, 1f)
        smoothedPressure = if (i == 0) {
            rawPressure
        } else {
            smoothedPressure * 0.72f + rawPressure * 0.28f
        }

        if (i > 0) {
            val prev = points[i - 1]
            val segDist = hypot((curr.x - prev.x).toFloat(), (curr.y - prev.y).toFloat())
            windowDist += segDist

            if (curr.timestampMs > 0L && points[windowStartIdx].timestampMs > 0L) {
                while (windowStartIdx < i - 1 &&
                    (curr.timestampMs - points[windowStartIdx].timestampMs) > 45L
                ) {
                    val a = points[windowStartIdx]
                    val b = points[windowStartIdx + 1]
                    windowDist = max(0f, windowDist - hypot((b.x - a.x).toFloat(), (b.y - a.y).toFloat()))
                    windowStartIdx++
                }
                val windowDt = (curr.timestampMs - points[windowStartIdx].timestampMs).coerceAtLeast(16L)
                val windowSpeed = windowDist / windowDt.toFloat()
                smoothedSpeed = smoothedSpeed * 0.80f + windowSpeed * 0.20f
            } else {
                val fallbackSpeed = segDist / 8f
                smoothedSpeed = smoothedSpeed * 0.80f + fallbackSpeed * 0.20f
            }
        }

        rawWidths[i] = calculateWidthForBrush(brush, smoothedPressure, smoothedSpeed)
    }

    if (n < 3) return rawWidths

    val forward = FloatArray(n)
    forward[0] = rawWidths[0]
    for (i in 1 until n) {
        val dist = hypot(
            (points[i].x - points[i - 1].x).toFloat(),
            (points[i].y - points[i - 1].y).toFloat()
        ).coerceAtLeast(0.5f)
        val maxStep = dist * 0.42f
        val target = forward[i - 1] * 0.70f + rawWidths[i] * 0.30f
        forward[i] = target.coerceIn(forward[i - 1] - maxStep, forward[i - 1] + maxStep)
    }

    val result = FloatArray(n)
    result[n - 1] = forward[n - 1]
    for (i in n - 2 downTo 0) {
        val dist = hypot(
            (points[i + 1].x - points[i].x).toFloat(),
            (points[i + 1].y - points[i].y).toFloat()
        ).coerceAtLeast(0.5f)
        val maxStep = dist * 0.42f
        val target = result[i + 1] * 0.65f + forward[i] * 0.35f
        result[i] = target.coerceIn(result[i + 1] - maxStep, result[i + 1] + maxStep)
    }

    return result
}

fun calculateWidthForBrush(
    brush: Brush,
    pressure: Float,
    speedPxPerMs: Float = 0f
): Float {
    val p = pressure.coerceIn(0f, 1f)
    return when (brush.type) {
        BrushType.BALLPOINT -> {
            brush.baseSize * (0.95f + p * 0.10f)
        }

        BrushType.FOUNTAIN -> {
            val pressureFactor = 0.4f + p * 1.2f
            val speedNorm = (speedPxPerMs / 2.2f).coerceIn(0f, 1f)
            val speedFactor = 1f - speedNorm * 0.58f
            brush.baseSize * pressureFactor * speedFactor
        }

        BrushType.HIGHLIGHTER -> {
            brush.baseSize * HIGHLIGHTER_WIDTH_MULTIPLIER
        }
    }.coerceAtLeast(0.5f)
}

private val forwardChiselMatrix = android.graphics.Matrix().apply {
    // Поворачиваем ось наконечника на горизонталь, растягиваем по Y в 4.5 раза и возвращаем угол
    postRotate(-HIGHLIGHTER_CHISEL_ANGLE_DEG)
    postScale(1f, 4.5f)
    postRotate(HIGHLIGHTER_CHISEL_ANGLE_DEG)
}

private fun applyChiselForwardTransform(path: Path) {
    path.asAndroidPath().transform(forwardChiselMatrix)
}