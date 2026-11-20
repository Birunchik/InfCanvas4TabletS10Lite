package com.infinitecanvas.board.testing

import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType

/**
 * Фабрики Brush для unit-тестов.
 *
 * Дефолты подобраны под читаемость ассертов: baseSize=10,
 * чёрный opaque. Если конкретному тесту нужно иное — передаётся явно.
 *
 * ВНИМАНИЕ: если конструктор Brush в проекте содержит дополнительные
 * обязательные поля (не входящие в дефолты), добавьте их в этот метод —
 * сигнатура фабрики остаётся прежней, изменения только здесь.
 */
object BrushFixtures {
    fun brush(
        type: BrushType,
        baseSize: Float = 10f,
        colorArgb: Int = 0xFF000000.toInt(),
        opacity: Float = 1f
    ): Brush = Brush(
        type = type,
        colorArgb = colorArgb,
        baseSize = baseSize,
        opacity = opacity
    )
}