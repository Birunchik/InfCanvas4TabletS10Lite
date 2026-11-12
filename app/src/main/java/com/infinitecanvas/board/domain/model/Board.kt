package com.infinitecanvas.board.domain.model

import android.graphics.Color
import android.graphics.RectF

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

enum class BrushType {
    BALLPOINT, // Шариковая ручка (стабильная ширина)
    FOUNTAIN,  // Перьевая ручка (зависимость от давления и угла наклона)
    PENCIL     // Карандаш (текстурная прозрачность)
}

data class Brush(
    val type: BrushType,
    val color: Int,
    val size: Float, // в пикселях мира
    val opacity: Float = 1f
)

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tilt: Float,
    val timestamp: Long
)

data class Stroke(
    val id: String,
    val brush: Brush,
    val points: List<StrokePoint>,
    val bounds: RectF // Кешированный bounding box для быстрой фильтрации Quadtree
)