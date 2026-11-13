package com.infinitecanvas.board.presentation.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.ViewportState
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

@Composable
fun InfiniteCanvasView(
    viewportState: ViewportState,
    strokes: List<Stroke>,
    activeBrush: Brush,
    allowFingerDrawing: Boolean = false,
    onStrokeCommitted: (Stroke) -> Unit,
    onTransform: (panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFF8F9FA),
    gridColor: Color = Color(0xFFE0E3E7),
    majorGridColor: Color = Color(0xFFC4C9D0)
) {
    val currentViewport by rememberUpdatedState(viewportState)
    val currentBrush by rememberUpdatedState(activeBrush)
    val currentOnCommit by rememberUpdatedState(onStrokeCommitted)
    val currentOnTransform by rememberUpdatedState(onTransform)

    val activePoints = remember { mutableStateListOf<StrokePoint>() }
    var activeTick by remember { mutableIntStateOf(0) }

    // Постоянный кэш готовых Path по ID штриха (не зависит от зума и сдвига!)
    val pathCache = remember { StrokeGeometryCache() }
    val activePath = remember { Path() }
    val minorGridPath = remember { Path() }
    val majorGridPath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(allowFingerDrawing) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val isStylus = firstDown.type == PointerType.Stylus ||
                            firstDown.type == PointerType.Eraser
                    val isSingleFingerAllowed = allowFingerDrawing &&
                            firstDown.type == PointerType.Touch

                    if (isStylus || isSingleFingerAllowed) {
                        val pointerId = firstDown.id
                        firstDown.consume()
                        activePoints.clear()
                        appendFilteredPoints(firstDown, currentViewport, activePoints)
                        activeTick++

                        while (true) {
                            val event: PointerEvent = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()

                            if (change == null || !change.pressed) {
                                if (change != null) {
                                    appendFilteredPoints(change, currentViewport, activePoints)
                                    change.consume()
                                }
                                if (activePoints.isNotEmpty()) {
                                    val committed = Stroke.create(
                                        id = System.nanoTime(),
                                        points = activePoints.toList(),
                                        brush = currentBrush
                                    )
                                    // Сразу прогреваем кэш геометрии для нового штриха
                                    pathCache.getOrBuild(committed)
                                    currentOnCommit(committed)
                                }
                                activePoints.clear()
                                activeTick++
                                break
                            } else {
                                if (appendFilteredPoints(change, currentViewport, activePoints)) {
                                    activeTick++
                                }
                                change.consume()
                            }
                        }
                    } else {
                        do {
                            val event = awaitPointerEvent()
                            val stylusAppeared = event.changes.any {
                                it.type == PointerType.Stylus || it.type == PointerType.Eraser
                            }
                            if (stylusAppeared) break

                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)

                            if (centroid != Offset.Unspecified && (zoom != 1f || pan != Offset.Zero)) {
                                currentOnTransform(pan.x, pan.y, zoom, centroid.x, centroid.y)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        activeTick

        val canvasWidth = size.width
        val canvasHeight = size.height

        val worldLeft = viewportState.screenToWorldX(0f)
        val worldTop = viewportState.screenToWorldY(0f)
        val worldRight = viewportState.screenToWorldX(canvasWidth)
        val worldBottom = viewportState.screenToWorldY(canvasHeight)

        // 1. Сетка за 2 GPU draw-call'а (один Path для обычных линий, один для главных)
        drawBatchedWorldGrid(
            viewportState = viewportState,
            worldLeft = worldLeft,
            worldTop = worldTop,
            worldRight = worldRight,
            worldBottom = worldBottom,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            gridColor = gridColor,
            majorGridColor = majorGridColor,
            minorGridPath = minorGridPath,
            majorGridPath = majorGridPath
        )

        // 2. Отрисовка закоммиченных штрихов из кэша (нулевой пересчёт геометрии при зуме/пане!)
        withTransform({
            translate(
                left = viewportState.offsetX.toFloat(),
                top = viewportState.offsetY.toFloat()
            )
            scale(
                scaleX = viewportState.scale.toFloat(),
                scaleY = viewportState.scale.toFloat(),
                pivot = Offset.Zero
            )
        }) {
            for (i in strokes.indices) {
                val stroke = strokes[i]
                if (!stroke.bounds.intersects(worldLeft, worldTop, worldRight, worldBottom)) {
                    continue
                }
                val cachedGeometry = pathCache.getOrBuild(stroke)
                drawCachedGeometry(cachedGeometry)
            }

            // 3. Активный штрих пера
            if (activePoints.isNotEmpty()) {
                drawDynamicStroke(activePoints, currentBrush, activePath)
            }
        }
    }
}

/**
 * Предвычисленная геометрия штриха в мировых координатах.
 */
private class CachedStrokeGeometry(
    val path: Path,
    val color: Color,
    val isFilledPolygon: Boolean,
    val strokeWidth: Float,
    val singleDotCenter: Offset? = null,
    val singleDotRadius: Float = 0f
)

/**
 * Кэширует готовые Path по ID штриха. При зуме и сдвиге геометрия не пересчитывается.
 */
private class StrokeGeometryCache {
    private val cache = HashMap<Long, CachedStrokeGeometry>(256)

    fun getOrBuild(stroke: Stroke): CachedStrokeGeometry {
        cache[stroke.id]?.let { return it }

        // Очищаем устаревшие записи, если штрихи были удалены через Undo
        if (cache.size > 2000) {
            cache.clear()
        }

        val built = buildGeometry(stroke.points, stroke.brush, Path())
        cache[stroke.id] = built
        return built
    }
}

private fun buildGeometry(
    points: List<StrokePoint>,
    brush: Brush,
    targetPath: Path
): CachedStrokeGeometry {
    val baseColor = Color(brush.colorArgb)
    val effectiveAlpha = when (brush.type) {
        BrushType.PENCIL -> (brush.opacity * 0.45f).coerceIn(0.1f, 1f)
        else -> brush.opacity
    }
    val strokeColor = baseColor.copy(alpha = effectiveAlpha)

    if (points.size == 1) {
        val p = points[0]
        return CachedStrokeGeometry(
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
                path = targetPath,
                color = strokeColor,
                isFilledPolygon = false,
                strokeWidth = width
            )
        }

        BrushType.FOUNTAIN -> {
            buildVariableWidthOutline(points, brush, targetPath)
            CachedStrokeGeometry(
                path = targetPath,
                color = strokeColor,
                isFilledPolygon = true,
                strokeWidth = 0f
            )
        }
    }
}

private fun DrawScope.drawCachedGeometry(geom: CachedStrokeGeometry) {
    val dotCenter = geom.singleDotCenter
    if (dotCenter != null) {
        drawCircle(
            color = geom.color,
            radius = geom.singleDotRadius,
            center = dotCenter
        )
        return
    }

    if (geom.isFilledPolygon) {
        drawPath(
            path = geom.path,
            color = geom.color
        )
    } else {
        drawPath(
            path = geom.path,
            color = geom.color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = geom.strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

private fun DrawScope.drawDynamicStroke(
    points: List<StrokePoint>,
    brush: Brush,
    reusablePath: Path
) {
    if (points.isEmpty()) return
    val geom = buildGeometry(points, brush, reusablePath)
    drawCachedGeometry(geom)
}

/**
 * Отрисовка фиксированной сетки всего за 2 GPU draw-call'а.
 */
private fun DrawScope.drawBatchedWorldGrid(
    viewportState: ViewportState,
    worldLeft: Double,
    worldTop: Double,
    worldRight: Double,
    worldBottom: Double,
    canvasWidth: Float,
    canvasHeight: Float,
    gridColor: Color,
    majorGridColor: Color,
    minorGridPath: Path,
    majorGridPath: Path
) {
    val fixedStep = 50.0
    val majorStep = fixedStep * 5.0

    val visibleColumns = (worldRight - worldLeft) / fixedStep
    val effectiveStep = if (visibleColumns > 250.0) majorStep else fixedStep

    val firstGridX = floor(worldLeft / effectiveStep) * effectiveStep
    val firstGridY = floor(worldTop / effectiveStep) * effectiveStep

    minorGridPath.reset()
    majorGridPath.reset()

    var currentX = firstGridX
    while (currentX <= worldRight) {
        val screenX = viewportState.worldToScreenX(currentX)
        val isMajor = abs(currentX % majorStep) < (fixedStep * 0.01) ||
                abs((currentX % majorStep) - majorStep) < (fixedStep * 0.01)

        val target = if (isMajor) majorGridPath else minorGridPath
        target.moveTo(screenX, 0f)
        target.lineTo(screenX, canvasHeight)
        currentX += effectiveStep
    }

    var currentY = firstGridY
    while (currentY <= worldBottom) {
        val screenY = viewportState.worldToScreenY(currentY)
        val isMajor = abs(currentY % majorStep) < (fixedStep * 0.01) ||
                abs((currentY % majorStep) - majorStep) < (fixedStep * 0.01)

        val target = if (isMajor) majorGridPath else minorGridPath
        target.moveTo(0f, screenY)
        target.lineTo(canvasWidth, screenY)
        currentY += effectiveStep
    }

    drawPath(
        path = minorGridPath,
        color = gridColor,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
    )
    drawPath(
        path = majorGridPath,
        color = majorGridColor,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
    )
}

@OptIn(ExperimentalComposeUiApi::class)
private fun appendFilteredPoints(
    change: PointerInputChange,
    viewport: ViewportState,
    target: MutableList<StrokePoint>
): Boolean {
    var added = false
    val minDistWorld = 1.2 / viewport.scale.coerceAtLeast(0.25)
    val basePressure = change.pressure.coerceIn(0.05f, 1f)

    for (historical in change.historical) {
        val wx = viewport.screenToWorldX(historical.position.x)
        val wy = viewport.screenToWorldY(historical.position.y)
        if (shouldAddPoint(target, wx, wy, minDistWorld)) {
            target.add(StrokePoint(wx, wy, basePressure))
            added = true
        }
    }

    val wx = viewport.screenToWorldX(change.position.x)
    val wy = viewport.screenToWorldY(change.position.y)
    if (shouldAddPoint(target, wx, wy, minDistWorld)) {
        target.add(StrokePoint(wx, wy, basePressure))
        added = true
    }
    return added
}

private fun shouldAddPoint(
    target: List<StrokePoint>,
    wx: Double,
    wy: Double,
    minDist: Double
): Boolean {
    if (target.isEmpty()) return true
    val last = target.last()
    return hypot(wx - last.x, wy - last.y) >= minDist
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

private fun calculateWidthForBrush(brush: Brush, pressure: Float): Float {
    return when (brush.type) {
        BrushType.BALLPOINT -> brush.baseSize
        BrushType.FOUNTAIN -> brush.baseSize * (0.2f + pressure * 2.2f)
        BrushType.PENCIL -> brush.baseSize * (0.45f + pressure * 0.55f)
    }.coerceAtLeast(0.5f)
}