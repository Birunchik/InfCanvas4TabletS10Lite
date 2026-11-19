package com.infinitecanvas.board.domain.model

// Порядок констант — часть бинарного формата: в BoardFileStorage пишется ordinal.
// Добавлять типы только в конец; перестановка требует ++FORMAT_VERSION.
enum class BrushType {
    BALLPOINT,     // Ручка
    FOUNTAIN,      // Перо
    PENCIL,        // Карандаш
    MARKER,        // Маркер
    HIGHLIGHTER    // Выделитель
}

// Итоговая прозрачность = alpha(colorArgb) * opacity; highlighter дополнительно
// зажимает opacity в 0.35..0.45, поэтому значение из UI не стоит считать точным.
data class Brush(
    val type: BrushType = BrushType.FOUNTAIN,
    val colorArgb: Int = 0xFF1A1C1E.toInt(),
    val baseSize: Float = 4f,
    val opacity: Float = 1f
)