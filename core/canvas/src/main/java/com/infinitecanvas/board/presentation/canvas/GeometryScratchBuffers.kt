package com.infinitecanvas.board.presentation.canvas

import kotlin.math.max

private const val MAX_POOLED_BUFFER_CAPACITY = 2048

// Скрэтч-массивы на поток: построение геометрии иначе аллоцировало бы 8 FloatArray на штрих,
// а это прямые GC-паузы при массовом кэш-миссе во время кадра.
internal class GeometryScratchBuffers {
    var xs1 = FloatArray(256)
    var ys1 = FloatArray(256)
    var xs2 = FloatArray(256)
    var ys2 = FloatArray(256)

    var rawWidths = FloatArray(256)
    var cumulativeDist = FloatArray(256)
    var forwardWidths = FloatArray(256)
    var backwardWidths = FloatArray(256)

    fun ensureCapacity(required: Int) {
        // Растим все массивы вместе: они индексируются одним i, и разная длина дала бы выход за границу.
        if (xs1.size < required) {
            val newCap = max(required, xs1.size * 2)
            xs1 = FloatArray(newCap)
            ys1 = FloatArray(newCap)
            xs2 = FloatArray(newCap)
            ys2 = FloatArray(newCap)
            rawWidths = FloatArray(newCap)
            cumulativeDist = FloatArray(newCap)
            forwardWidths = FloatArray(newCap)
            backwardWidths = FloatArray(newCap)
        }
    }

    fun trimIfOversized() {
        // Сбрасываем разросшиеся буферы: иначе один гигантский штрих навсегда оставил бы их память в ThreadLocal.
        if (xs1.size > MAX_POOLED_BUFFER_CAPACITY) {
            xs1 = FloatArray(256)
            ys1 = FloatArray(256)
            xs2 = FloatArray(256)
            ys2 = FloatArray(256)
            rawWidths = FloatArray(256)
            cumulativeDist = FloatArray(256)
            forwardWidths = FloatArray(256)
            backwardWidths = FloatArray(256)
        }
    }
}

// ThreadLocal, а не поле класса: буферы переиспользуются без блокировок и не делятся между экземплярами кэша.
// internal: используется построителем геометрии из соседнего файла (StrokeGeometryBuilder.kt).
internal val threadLocalScratchBuffers = ThreadLocal.withInitial { GeometryScratchBuffers() }
