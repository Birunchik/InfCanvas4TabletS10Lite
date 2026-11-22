package com.infinitecanvas.board.testing

import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint

/**
 * Дубль src/test/testing/StrokeRealFixtures: минимальный набор для androidTest.
 * stroke через Stroke.create — bounds вычисляются по точкам + padding кисти.
 */
object StrokeRealFixtures {

    fun points(vararg xy: Pair<Double, Double>): List<StrokePoint> =
        xy.map { (x, y) -> StrokePoint(x, y, pressure = 0.5f, timestampMs = 0L) }

    fun created(id: Long, vararg xy: Pair<Double, Double>, brush: Brush): Stroke =
        Stroke.create(id = id, points = points(*xy), brush = brush)
}