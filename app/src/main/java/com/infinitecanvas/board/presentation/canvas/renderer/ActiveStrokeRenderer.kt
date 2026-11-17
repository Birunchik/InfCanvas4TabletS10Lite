package com.infinitecanvas.board.presentation.canvas.renderer

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.presentation.canvas.CachedStrokeGeometry
import com.infinitecanvas.board.presentation.canvas.buildGeometry

/**
 * Кэш геометрии активного штриха: пересобираем path только при изменении
 * количества точек. Инвалидируется при count == 0.
 */
class ActiveStrokeCache {
    var pointCount: Int = -1
    var geometry: CachedStrokeGeometry? = null

    fun reset() {
        pointCount = 0
        geometry = null
    }
}

/**
 * Отрисовка штриха, который сейчас рисуется пользователем (до коммита).
 * Вызывать внутри withTransform.
 */
fun DrawScope.drawActiveStroke(
    activePointCount: Int,
    createStroke: () -> Stroke,
    activePath: Path,
    cache: ActiveStrokeCache
) {
    if (activePointCount > 0) {
        val geom = if (activePointCount == cache.pointCount && cache.geometry != null) {
            cache.geometry!!
        } else {
            val tempStroke = createStroke()
            buildGeometry(tempStroke, activePath).also {
                cache.pointCount = activePointCount
                cache.geometry = it
            }
        }
        drawCachedGeometry(geom)
    } else if (cache.pointCount != 0) {
        cache.reset()
    }
}