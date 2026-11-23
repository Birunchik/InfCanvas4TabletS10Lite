package com.infinitecanvas.board.domain.model
import com.infinitecanvas.board.domain.model.BrushDefaults.HIGHLIGHTER_WIDTH_MULTIPLIER

// Порядок констант — часть бинарного формата: в BoardFileStorage пишется ordinal.
// Добавлять типы только в конец; перестановка требует ++FORMAT_VERSION.
enum class BrushType {
    BALLPOINT,     // Ручка
    FOUNTAIN,      // Перо
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
// Ширина возвращается в world px: при рендере её умножает масштаб трансформа,
// поэтому zoom здесь не учитывается.
fun Brush.calculateWidth(pressure: Float, speedPxPerMs: Float = 0f): Float {
    val p = pressure.coerceIn(0f, 1f)
    return when (type) {
        BrushType.BALLPOINT  -> baseSize * (0.95f + p * 0.10f)
        BrushType.MARKER     -> baseSize * (0.98f + p * 0.05f)
        BrushType.HIGHLIGHTER-> baseSize * HIGHLIGHTER_WIDTH_MULTIPLIER
        BrushType.FOUNTAIN   -> {
            val pressureFactor = 0.4f + p * 1.2f
            val speedNorm = (speedPxPerMs / 2.2f).coerceIn(0f, 1f)
            val speedFactor = 1f - speedNorm * 0.58f
            baseSize * pressureFactor * speedFactor
        }
    }.coerceAtLeast(0.5f)
}