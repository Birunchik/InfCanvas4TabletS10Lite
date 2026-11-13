package com.infinitecanvas.board.domain.model

enum class BrushType {

    BALLPOINT,  // Шариковая ручка: равномерная толщина
    FOUNTAIN,   //Перьевая ручка: толщина от силы нажатия S Pen.
    PENCIL   //Карандаш: полупрозрачный след
}

data class Brush(
    val type: BrushType = BrushType.FOUNTAIN,
    val colorArgb: Int = 0xFF1A1C1E.toInt(),
    val baseSize: Float = 4f,
    val opacity: Float = 1f
)