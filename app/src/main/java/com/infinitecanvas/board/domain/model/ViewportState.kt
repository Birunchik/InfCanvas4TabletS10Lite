package com.infinitecanvas.board.domain.model

import kotlin.math.max
import kotlin.math.min
// offset — экранные px, scale — безразмерный. Прямое преобразование:
// screen = world * scale + offset; обратное сначала вычитает offset, потом делит.
data class ViewportState(
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val scale: Double = 1.0
) {
    companion object {
        const val MIN_SCALE = 0.15  // 15%
        const val MAX_SCALE = 1.5  // 150%
        fun clampScale(targetScale: Double): Double {
            return min(MAX_SCALE, max(MIN_SCALE, targetScale))
        }
    }

    //экранные координаты (px) в мировые .
    fun screenToWorldX(screenX: Float): Double = (screenX - offsetX) / scale
    fun screenToWorldY(screenY: Float): Double = (screenY - offsetY) / scale

    // мировые координаты в экранные (px).
    fun worldToScreenX(worldX: Double): Float = (worldX * scale + offsetX).toFloat()
    fun worldToScreenY(worldY: Double): Float = (worldY * scale + offsetY).toFloat()
}