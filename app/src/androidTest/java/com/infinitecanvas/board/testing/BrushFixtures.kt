package com.infinitecanvas.board.testing

import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType

/**
 * Дубль src/test/testing/BrushFixtures: androidTest и test не видят друг друга
 * (AGP 9.x: sourceSets srcDir не резолвится между test и androidTest).
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