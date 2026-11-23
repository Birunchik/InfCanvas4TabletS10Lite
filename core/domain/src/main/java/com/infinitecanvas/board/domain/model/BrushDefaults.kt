package com.infinitecanvas.board.domain.model

/**
 * Физические константы кистей, не зависящие от платформы.
 * Используются как рендерером (отрисовка), так и Quadtree (hit-testing).
 */
object BrushDefaults {

    const val HIGHLIGHTER_WIDTH_MULTIPLIER = 6.0f
    const val HIGHLIGHTER_CHISEL_ANGLE_DEG = -30f
}