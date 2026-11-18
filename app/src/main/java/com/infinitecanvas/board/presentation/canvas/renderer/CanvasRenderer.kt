package com.infinitecanvas.board.presentation.canvas.renderer

import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.presentation.canvas.CachedStrokeGeometry

const val HIGHLIGHTER_WIDTH_MULTIPLIER = 6.0f
const val HIGHLIGHTER_CHISEL_ANGLE_DEG = -30f

private val highlighterLayerPaint = Paint()

private val strokeCache = HashMap<Float, Stroke>()

private val forwardChiselMatrix = Matrix().apply {
    postRotate(-HIGHLIGHTER_CHISEL_ANGLE_DEG)
    postScale(1f, 4.5f)
    postRotate(HIGHLIGHTER_CHISEL_ANGLE_DEG)
}

private fun getStroke(width: Float): Stroke {
    return strokeCache.getOrPut(width) {
        Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
}
fun applyChiselForwardTransform(path: Path) {
    path.asAndroidPath().transform(forwardChiselMatrix)
}

/**
 * Отрисовка кэшированной геометрии штриха (точки, выделителя, пера или вариативных сегментов).
 */
fun DrawScope.drawCachedGeometry(
    geom: CachedStrokeGeometry,
    visibleWorldBounds: WorldBounds? = null
) {
    val dotCenter = geom.singleDotCenter
    if (dotCenter != null) {
        drawCircle(
            color = geom.color,
            radius = geom.singleDotRadius,
            center = dotCenter
        )
        return
    }

    if (geom.isHighlighter) {
        val layerBounds = Rect(
            left = geom.bounds.minX.toFloat(),
            top = geom.bounds.minY.toFloat(),
            right = geom.bounds.maxX.toFloat(),
            bottom = geom.bounds.maxY.toFloat()
        )
        val opaqueColor = geom.color.copy(alpha = 1f)
        highlighterLayerPaint.alpha = geom.color.alpha

        drawIntoCanvas { canvas ->
            canvas.saveLayer(layerBounds, highlighterLayerPaint)
            try {
                withTransform({
                    rotate(degrees = HIGHLIGHTER_CHISEL_ANGLE_DEG, pivot = Offset.Zero)
                    scale(scaleX = 1f, scaleY = 1f / 4.5f, pivot = Offset.Zero)
                    rotate(degrees = -HIGHLIGHTER_CHISEL_ANGLE_DEG, pivot = Offset.Zero)
                }) {
                    drawPath(
                        path = geom.path,
                        color = opaqueColor,
                        style = getStroke(geom.strokeWidth)
                    )
                }
            } finally {
                canvas.restore()
            }
        }
        return
    }

    if (geom.variableSegments.isNotEmpty()) {
        for (i in geom.variableSegments.indices) {
            val seg = geom.variableSegments[i]
            if (visibleWorldBounds != null && !seg.bounds.intersects(visibleWorldBounds)) {
                continue
            }
            drawPath(
                path = seg.path,
                color = geom.color,
                style = getStroke(geom.strokeWidth)
            )
        }
        return
    }

    drawPath(
        path = geom.path,
        color = geom.color,
        style = getStroke(geom.strokeWidth)
    )
}