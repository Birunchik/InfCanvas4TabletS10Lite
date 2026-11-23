package com.infinitecanvas.board.domain.model

// bounds — мировые координаты, но при isPinned=true они хранятся в экранных px:
// закреплённая картинка не сдвигается вместе с холстом (см. ImageGestureHandler).
data class BoardImage(
    val id: Long,
    val fileName: String,
    val bounds: WorldBounds,
    val zIndex: Int = 0,
    val isPinned: Boolean = false
) {
    val width: Double get() = bounds.maxX - bounds.minX
    val height: Double get() = bounds.maxY - bounds.minY
    fun resized(newBounds: WorldBounds): BoardImage = copy(bounds = newBounds)
}