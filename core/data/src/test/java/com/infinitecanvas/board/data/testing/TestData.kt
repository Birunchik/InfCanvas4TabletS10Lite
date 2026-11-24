package com.infinitecanvas.board.data.testing

import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds

fun testStroke(
    id: Long = 1L,
    points: List<StrokePoint> = listOf(
        StrokePoint(x = 0.0, y = 0.0, pressure = 0.5f, timestampMs = 0L),
        StrokePoint(x = 10.0, y = 10.0, pressure = 0.5f, timestampMs = 16L)
    ),
    brush: Brush = Brush(
        type = BrushType.BALLPOINT,
        colorArgb = 0xFF000000.toInt(),
        baseSize = 4f,
        opacity = 1f
    ),
    bounds: WorldBounds = WorldBounds(0.0, 0.0, 10.0, 10.0)
): Stroke = Stroke(id = id, points = points, brush = brush, bounds = bounds)

fun testImage(
    id: Long = 100L,
    fileName: String = "img.webp",
    bounds: WorldBounds = WorldBounds(0.0, 0.0, 100.0, 100.0),
    zIndex: Int = 0,
    isPinned: Boolean = false
): BoardImage = BoardImage(
    id = id,
    fileName = fileName,
    bounds = bounds,
    zIndex = zIndex,
    isPinned = isPinned
)

fun testViewport(
    offsetX: Double = 1.5,
    offsetY: Double = -2.5,
    scale: Double = 2.0
): ViewportState = ViewportState(offsetX = offsetX, offsetY = offsetY, scale = scale)