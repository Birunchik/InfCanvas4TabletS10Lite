package com.infinitecanvas.board.presentation.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.presentation.canvas.renderer.HIGHLIGHTER_WIDTH_MULTIPLIER
import com.infinitecanvas.board.presentation.canvas.renderer.applyChiselForwardTransform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val MAX_GEOMETRY_CACHE_ENTRIES = 1500
private const val MAX_POOLED_BUFFER_CAPACITY = 2048

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
    val variableSegments: List<VariableStrokeSegment> = emptyList(),
    val brushType: BrushType? = null
)

internal class GeometryScratchBuffers {
    var xs1 = FloatArray(256)
    var ys1 = FloatArray(256)
    var xs2 = FloatArray(256)
    var ys2 = FloatArray(256)

    var rawWidths = FloatArray(256)
    var cumulativeDist = FloatArray(256)
    var forwardWidths = FloatArray(256)
    var backwardWidths = FloatArray(256)

    fun ensureCapacity(required: Int) {
        if (xs1.size < required) {
            val newCap = max(required, xs1.size * 2)
            xs1 = FloatArray(newCap)
            ys1 = FloatArray(newCap)
            xs2 = FloatArray(newCap)
            ys2 = FloatArray(newCap)
            rawWidths = FloatArray(newCap)
            cumulativeDist = FloatArray(newCap)
            forwardWidths = FloatArray(newCap)
            backwardWidths = FloatArray(newCap)
        }
    }

    fun trimIfOversized() {
        if (xs1.size > MAX_POOLED_BUFFER_CAPACITY) {
            xs1 = FloatArray(256)
            ys1 = FloatArray(256)
            xs2 = FloatArray(256)
            ys2 = FloatArray(256)
            rawWidths = FloatArray(256)
            cumulativeDist = FloatArray(256)
            forwardWidths = FloatArray(256)
            backwardWidths = FloatArray(256)
        }
    }
}

private val threadLocalScratchBuffers = ThreadLocal.withInitial { GeometryScratchBuffers() }

class StrokeGeometryCache {
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
    val n = rawPoints.size

    if (n == 1) {
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
                strokeWidth = effectiveWidth,
                brushType = brush.type
            )
        }
        return CachedStrokeGeometry(
            bounds = effectiveBounds,
            path = targetPath,
            color = strokeColor,
            isHighlighter = false,
            singleDotCenter = Offset(p.x.toFloat(), p.y.toFloat()),
            singleDotRadius = calculateWidthForBrush(brush, p.pressure, speedPxPerMs = 0f) * 0.5f,
            brushType = brush.type
        )
    }

    val scratch = threadLocalScratchBuffers.get()!!
    scratch.ensureCapacity(n)

    try {
        smoothCoordinatesIntoScratch(rawPoints, n, scratch)
        val sx = scratch.xs1
        val sy = scratch.ys1

        return when (brush.type) {
            BrushType.HIGHLIGHTER -> {
                buildSmoothQuadraticPathFromArrays(sx, sy, n, targetPath)
                applyChiselForwardTransform(targetPath)
                CachedStrokeGeometry(
                    bounds = effectiveBounds,
                    path = targetPath,
                    color = strokeColor,
                    isHighlighter = true,
                    strokeWidth = effectiveWidth,
                    brushType = brush.type
                )
            }

            BrushType.BALLPOINT, BrushType.MARKER -> {
                buildSmoothQuadraticPathFromArrays(sx, sy, n, targetPath)
                var pressureSum = 0f
                for (i in 0 until n) {
                    pressureSum += rawPoints[i].pressure
                }
                val width = calculateWidthForBrush(brush, pressureSum / n, 0f)
                CachedStrokeGeometry(
                    bounds = effectiveBounds,
                    path = targetPath,
                    color = strokeColor,
                    isHighlighter = false,
                    strokeWidth = width,
                    brushType = brush.type
                )
            }

            BrushType.PENCIL -> {
                // Просто строим обычный гладкий путь. Никакого джиттера.
                buildSmoothQuadraticPathFromArrays(sx, sy, n, targetPath)
                CachedStrokeGeometry(
                    bounds = effectiveBounds,
                    path = targetPath,
                    color = strokeColor,
                    isHighlighter = false,
                    strokeWidth = effectiveWidth, // Оставляем стандартную ширину
                    brushType = brush.type
                )
            }

            BrushType.FOUNTAIN -> {
                val widths = computeSmoothedWidthsInScratch(rawPoints, sx, sy, n, brush, scratch)
                buildVariableWidthOutlinePath(rawPoints, widths, targetPath)

                CachedStrokeGeometry(
                    bounds = effectiveBounds,
                    path = targetPath,
                    color = strokeColor,
                    isHighlighter = false,
                    strokeWidth = 0f,
                    variableSegments = emptyList(), // Отключаем кусочки
                    brushType = brush.type
                )
            }
        }
    } finally {
        scratch.trimIfOversized()
    }
}

private fun smoothCoordinatesIntoScratch(
    raw: List<StrokePoint>,
    n: Int,
    scratch: GeometryScratchBuffers
) {
    val x1 = scratch.xs1
    val y1 = scratch.ys1
    for (i in 0 until n) {
        val pt = raw[i]
        x1[i] = pt.x.toFloat()
        y1[i] = pt.y.toFloat()
    }
    if (n < 3) return

    val x2 = scratch.xs2
    val y2 = scratch.ys2

    x2[0] = x1[0]
    y2[0] = y1[0]
    for (i in 1 until n - 1) {
        x2[i] = x1[i - 1] * 0.25f + x1[i] * 0.50f + x1[i + 1] * 0.25f
        y2[i] = y1[i - 1] * 0.25f + y1[i] * 0.50f + y1[i + 1] * 0.25f
    }
    x2[n - 1] = x1[n - 1]
    y2[n - 1] = y1[n - 1]

    for (i in 1 until n - 1) {
        x1[i] = x2[i - 1] * 0.25f + x2[i] * 0.50f + x2[i + 1] * 0.25f
        y1[i] = y2[i - 1] * 0.25f + y2[i] * 0.50f + y2[i + 1] * 0.25f
    }
}

private fun buildSmoothQuadraticPathFromArrays(
    xs: FloatArray,
    ys: FloatArray,
    n: Int,
    outPath: Path
) {
    outPath.moveTo(xs[0], ys[0])
    for (i in 1 until n) {
        outPath.lineTo(xs[i], ys[i])
    }
}

private fun buildQuantizedFountainSegments(
    rawPoints: List<StrokePoint>,
    xs: FloatArray,
    ys: FloatArray,
    n: Int,
    brush: Brush,
    scratch: GeometryScratchBuffers
): List<VariableStrokeSegment> {
    if (n < 2) return emptyList()

    val widths = computeSmoothedWidthsInScratch(rawPoints, xs, ys, n, brush, scratch)
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
            result.add(createSmoothSegmentFromArrays(xs, ys, segStartIdx, i, avgWidth))
            segStartIdx = i
            currentBucketWidth = w
        }
    }

    return result
}

private fun createSmoothSegmentFromArrays(
    xs: FloatArray,
    ys: FloatArray,
    startIdx: Int,
    endIdx: Int,
    width: Float
): VariableStrokeSegment {
    val path = Path()
    val x0 = xs[startIdx]
    val y0 = ys[startIdx]
    path.moveTo(x0, y0)

    var minX = x0
    var minY = y0
    var maxX = x0
    var maxY = y0

    for (i in (startIdx + 1)..endIdx) {
        val prevX = xs[i - 1]
        val prevY = ys[i - 1]
        val currX = xs[i]
        val currY = ys[i]

        if (currX < minX) minX = currX
        if (currY < minY) minY = currY
        if (currX > maxX) maxX = currX
        if (currY > maxY) maxY = currY

        if (i == endIdx) {
            path.lineTo(currX, currY)
        } else {
            val midX = (prevX + currX) * 0.5f
            val midY = (prevY + currY) * 0.5f
            path.quadraticTo(prevX, prevY, midX, midY)
        }
    }

    val pad = (width * 0.65).coerceAtLeast(2.0)
    return VariableStrokeSegment(
        path = path,
        width = width,
        bounds = WorldBounds(
            minX = minX - pad,
            minY = minY - pad,
            maxX = maxX + pad,
            maxY = maxY + pad
        )
    )
}

private fun computeSmoothedWidthsInScratch(
    rawPoints: List<StrokePoint>,
    xs: FloatArray,
    ys: FloatArray,
    n: Int,
    brush: Brush,
    scratch: GeometryScratchBuffers
): FloatArray {
    val rawWidths = scratch.rawWidths
    val cumDist = scratch.cumulativeDist
    cumDist[0] = 0f

    var smoothedPressure = rawPoints[0].pressure.coerceIn(0.05f, 1f)
    var smoothedSpeed = 0f
    var windowStartIdx = 0
    var windowDist = 0f

    for (i in 0 until n) {
        val currPt = rawPoints[i]
        val rawPressure = currPt.pressure.coerceIn(0.05f, 1f)
        smoothedPressure = if (i == 0) {
            rawPressure
        } else {
            smoothedPressure * 0.72f + rawPressure * 0.28f
        }

        if (i > 0) {
            val segDist = hypotFast(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            cumDist[i] = cumDist[i - 1] + segDist
            windowDist += segDist

            val currTs = currPt.timestampMs
            val startTs = rawPoints[windowStartIdx].timestampMs
            if (currTs > 0L && startTs > 0L) {
                while (windowStartIdx < i - 1 &&
                    (currTs - rawPoints[windowStartIdx].timestampMs) > 45L
                ) {
                    val d = hypotFast(
                        xs[windowStartIdx + 1] - xs[windowStartIdx],
                        ys[windowStartIdx + 1] - ys[windowStartIdx]
                    )
                    windowDist = max(0f, windowDist - d)
                    windowStartIdx++
                }
                val windowDt = (currTs - rawPoints[windowStartIdx].timestampMs).coerceAtLeast(16L)
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

    val forward = scratch.forwardWidths
    forward[0] = rawWidths[0]
    for (i in 1 until n) {
        val dist = (cumDist[i] - cumDist[i - 1]).coerceAtLeast(0.5f)
        val maxStep = dist * 0.42f
        val target = forward[i - 1] * 0.70f + rawWidths[i] * 0.30f
        forward[i] = target.coerceIn(forward[i - 1] - maxStep, forward[i - 1] + maxStep)
    }

    val backward = scratch.backwardWidths
    backward[n - 1] = forward[n - 1]
    for (i in n - 2 downTo 0) {
        val dist = (cumDist[i + 1] - cumDist[i]).coerceAtLeast(0.5f)
        val maxStep = dist * 0.42f
        val target = backward[i + 1] * 0.65f + forward[i] * 0.35f
        backward[i] = target.coerceIn(backward[i + 1] - maxStep, backward[i + 1] + maxStep)
    }

    return backward
}

private fun buildVariableWidthOutlinePath(
    points: List<StrokePoint>,
    widths: FloatArray,
    outPath: Path
) {
    val n = points.size
    if (n < 2) return

    val halfWidths = FloatArray(n) { widths[it] * 0.5f }
    val leftX = FloatArray(n)
    val leftY = FloatArray(n)
    val rightX = FloatArray(n)
    val rightY = FloatArray(n)

    // Вычисляем векторы нормалей и смещаем точки
    for (i in 0 until n) {
        val curr = points[i]
        val prev = if (i > 0) points[i - 1] else curr
        val next = if (i < n - 1) points[i + 1] else curr

        var dx = (next.x - prev.x).toFloat()
        var dy = (next.y - prev.y).toFloat()
        val len = sqrt(dx * dx + dy * dy)
        if (len > 1e-4f) { dx /= len; dy /= len } else { dx = 1f; dy = 0f }

        val hw = halfWidths[i]
        leftX[i] = curr.x.toFloat() - dy * hw
        leftY[i] = curr.y.toFloat() + dx * hw
        rightX[i] = curr.x.toFloat() + dy * hw
        rightY[i] = curr.y.toFloat() - dx * hw
    }

    // Левая сторона (плавные кривые)
    outPath.moveTo(leftX[0], leftY[0])
    for (i in 1 until n) {
        val midX = (leftX[i - 1] + leftX[i]) * 0.5f
        val midY = (leftY[i - 1] + leftY[i]) * 0.5f
        outPath.quadraticTo(leftX[i - 1], leftY[i - 1], midX, midY)
    }
    outPath.lineTo(leftX[n - 1], leftY[n - 1])

    // Правая сторона (в обратном порядке, плавные кривые)
    outPath.lineTo(rightX[n - 1], rightY[n - 1])
    for (i in n - 1 downTo 1) {
        val midX = (rightX[i] + rightX[i - 1]) * 0.5f
        val midY = (rightY[i] + rightY[i - 1]) * 0.5f
        outPath.quadraticTo(rightX[i], rightY[i], midX, midY)
    }
    outPath.lineTo(rightX[0], rightY[0])
    outPath.close()
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

        BrushType.PENCIL -> {
            brush.baseSize * (0.85f + p * 0.15f)
        }

        BrushType.MARKER -> {
            brush.baseSize * (0.98f + p * 0.05f)
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

private fun hypotFast(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)