package com.infinitecanvas.board.presentation.canvas

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.presentation.canvas.renderer.applyChiselForwardTransform
import com.infinitecanvas.board.domain.model.calculateWidth
import kotlin.math.max
import kotlin.math.sqrt

// 1500 записей — компромисс: при сильном зуме в кадр попадают тысячи штрихов,
// и маленький LRU заставлял бы перестраивать Path прямо в draw-фазе.
private const val MAX_GEOMETRY_CACHE_ENTRIES = 1500
private const val MAX_POOLED_BUFFER_CAPACITY = 2048

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
// Скрэтч-массивы на поток: построение геометрии иначе аллоцировало бы 8 FloatArray на штрих,
// а это прямые GC-паузы при массовом кэш-миссе во время кадра.
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
        // Растим все массивы вместе: они индексируются одним i, и разная длина дала бы выход за границу.
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
        // Сбрасываем разросшиеся буферы: иначе один гигантский штрих навсегда оставил бы их память в ThreadLocal.
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

// ThreadLocal, а не поле класса: буферы переиспользуются без блокировок и не делятся между экземплярами кэша.
private val threadLocalScratchBuffers = ThreadLocal.withInitial { GeometryScratchBuffers() }

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

// Путь не создаём внутри: активный штрих переиспользует renderState.activePath,
// поэтому функция обязана начинать с reset и целиком перезаписывать targetPath.
internal fun buildGeometry(
    stroke: Stroke,
    targetPath: Path
): CachedStrokeGeometry {
    val rawPoints = stroke.points
    val brush = stroke.brush
    val baseColor = Color(brush.colorArgb)
    val isHighlighter = brush.type == BrushType.HIGHLIGHTER
    // Альфу highlighters зажимаем в 0.35–0.45: при полной альфе перекрытия выглядели бы как плотный маркер.
    val effectiveAlpha = if (isHighlighter) {
        brush.opacity.coerceIn(0.35f, 0.45f)
    } else {
        brush.opacity
    }
    val strokeColor = baseColor.copy(alpha = effectiveAlpha)

    val effectiveWidth = brush.calculateWidth(pressure = 0.5f, speedPxPerMs = 0f)
    // Bounds highlighters расширяем на 0.85 ширины: chisel-трансформ растягивает путь по Y,
    // и по исходным bounds отсечение обрезало бы концы штриха.
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
            singleDotRadius = brush.calculateWidth(p.pressure, speedPxPerMs = 0f) * 0.5f,
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
                val width = brush.calculateWidth(pressureSum / n, 0f)
                CachedStrokeGeometry(
                    bounds = effectiveBounds,
                    path = targetPath,
                    color = strokeColor,
                    isHighlighter = false,
                    strokeWidth = width,
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

// Два прохода box-фильтра 1-2-1: первый — в xs2, второй возвращает результат в xs1.
// Края не сглаживаем, иначе штрих «уезжал» бы с точки касания.
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

// Несмотря на имя, путь собирается lineTo: точки уже сглажены предыдущим проходом,
// и кривые Безье здесь только увеличили бы стоимость растеризации на длинных штрихах.
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
// Скорость берём по скользящему окну ~45 мс, а не по последнему сегменту:
// timestamp'ы дрожат, и мгновенная скорость давала бы рваную ширину.
// Два прохода (вперёд/назад) с лимитом шага на пиксель убирают асимметрию на быстрых росчерках.
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

        rawWidths[i] = brush.calculateWidth(smoothedPressure, smoothedSpeed)
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

// Нормаль считаем по (next - prev), а не по одному сегменту: иначе на изломах
// outline самопересекается и даёт «шипы». Здесь же аллокации на каждый вызов —
// поэтому FOUNTAIN дорог и не должен пересобираться каждый кадр.
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
private fun hypotFast(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)
