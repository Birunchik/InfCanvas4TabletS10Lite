package com.infinitecanvas.board.domain.model

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