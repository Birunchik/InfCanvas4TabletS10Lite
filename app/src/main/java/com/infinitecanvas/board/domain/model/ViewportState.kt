package com.infinitecanvas.board.domain.model

import kotlin.math.max
import kotlin.math.min

/**
 * Состояние камеры (вьюпорта) в мировых координатах.
 * Использует Double для сохранения высокой точности при экстремальном масштабировании (5% - 3200%).
 */
data class ViewportState(
    val offsetX: Double = 0.0, // Смещение камеры по X в мировых координатах
    val offsetY: Double = 0.0, // Смещение камеры по Y в мировых координатах
    val scale: Double = 1.0    // Коэффициент масштабирования (1.0 = 100%)
) {
    companion object {
        const val MIN_SCALE = 0.15  // 15%
        const val MAX_SCALE = 1.5  // 150%

        fun clampScale(targetScale: Double): Double {
            return min(MAX_SCALE, max(MIN_SCALE, targetScale))
        }
    }

    /**
     * Перевод экранных координат (px) в мировые координаты доски.
     */
    fun screenToWorldX(screenX: Float): Double = (screenX - offsetX) / scale
    fun screenToWorldY(screenY: Float): Double = (screenY - offsetY) / scale

    /**
     * Перевод мировых координат в экранные (px).
     */
    fun worldToScreenX(worldX: Double): Float = (worldX * scale + offsetX).toFloat()
    fun worldToScreenY(worldY: Double): Float = (worldY * scale + offsetY).toFloat()
}