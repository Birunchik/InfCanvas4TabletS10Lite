package com.infinitecanvas.board.domain.model

// Только UI-состояние: в BoardFileStorage не пишется; значение по умолчанию — BRUSH.
enum class CanvasTool {
    BRUSH,
    ERASER,
    LASSO
}