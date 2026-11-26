package com.infinitecanvas.board.testing

import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.WorldBounds
import io.mockk.every
import io.mockk.mockk

/**
 * Мок Stroke: тестам логики выделения нужны только id и bounds,
 * а не реальная геометрия через Stroke.create().
 *
 * Изоляция от изменений в Stroke.create()/bounds-вычислении:
 * например, если Stroke.bounds завтра начнёт включать strokeWidth/2,
 * эти тесты останутся зелёными, т.к. bounds задан явно.
 */
object StrokeFixtures {
    fun stroke(id: Long, bounds: WorldBounds): Stroke {
        val stroke: Stroke = mockk()
        every { stroke.id } returns id
        every { stroke.bounds } returns bounds
        return stroke
    }
}