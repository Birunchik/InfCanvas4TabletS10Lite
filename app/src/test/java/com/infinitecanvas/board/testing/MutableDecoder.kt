package com.infinitecanvas.board.testing

import androidx.compose.ui.graphics.ImageBitmap
import com.infinitecanvas.board.presentation.canvas.ImageDecoder
import java.io.File

/**
 * Управляемый декодер для BoardImageCacheTest.
 *
 * Хранит сколько раз вызывался и что возвращать. Позволяет тесту:
 *  - проверить in-flight dedup (callCount остаётся 1)
 *  - проверить null-путь (result = null)
 *  - переключить поведение между вызовами (result = другой bitmap)
 */
class MutableDecoder(
    var result: ImageBitmap? = null
) : ImageDecoder {

    var callCount: Int = 0
        private set

    var lastFile: File? = null
        private set

    override fun decode(file: File): ImageBitmap? {
        callCount++
        lastFile = file
        return result
    }
}