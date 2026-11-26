package com.infinitecanvas.board.testing

import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.WorldBounds

/**
 * Фабрики реалистичных Stroke для тестов buildGeometry / StrokeGeometryCache.
 *
 * Отличается от StrokeFixtures (mockk-based): здесь настоящие экземпляры
 * через Stroke.create / конструктор, потому что buildGeometry читает
 * points[i].x, brush.type, stroke.bounds напрямую.
 */
object StrokeRealFixtures {

    fun points(vararg xy: Pair<Double, Double>): List<StrokePoint> =
        xy.map { (x, y) -> StrokePoint(x, y, pressure = 0.5f, timestampMs = 0L) }

    fun pointsWithPressure(vararg xyp: Triple<Double, Double, Float>): List<StrokePoint> =
        xyp.map { (x, y, p) -> StrokePoint(x, y, p, timestampMs = 0L) }

    /** Реалистичный stroke через Stroke.create (bounds вычисляются по точкам + padding кисти). */
    fun created(id: Long, vararg xy: Pair<Double, Double>, brush: Brush): Stroke =
        Stroke.create(id = id, points = points(*xy), brush = brush)

    /** Stroke с явно заданными bounds — для проверки расширения highlighter'ом. */
    fun withExplicitBounds(
        id: Long,
        points: List<StrokePoint>,
        brush: Brush,
        bounds: WorldBounds
    ): Stroke = Stroke(id = id, points = points, brush = brush, bounds = bounds)
}