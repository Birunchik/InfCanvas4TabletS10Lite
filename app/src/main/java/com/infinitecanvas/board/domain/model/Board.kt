package com.infinitecanvas.board.domain.model

import android.graphics.Color

enum class BackgroundType { SOLID, GRID, DOTS, GRAPH_PAPER }

data class Board(
    val id: String,
    val title: String,
    val version: Int = 1,
    val worldWidth: Double = 10000.0,
    val worldHeight: Double = 10000.0,
    val backgroundColor: Int = Color.WHITE,
    val backgroundType: BackgroundType = BackgroundType.GRID,
    val layers: List<Layer> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Layer(
    val id: String,
    val name: String,
    val zIndex: Int,
    val isVisible: Boolean = true,
    val opacity: Float = 1f,
    val strokes: List<Stroke> = emptyList()
)

