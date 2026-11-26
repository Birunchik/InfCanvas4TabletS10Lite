package com.infinitecanvas.board.presentation.canvas.renderer

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.presentation.canvas.StrokeGeometryCache

// Два прохода, highlighters первыми: они полупрозрачные и должны лежать под остальными,
// порядок задаёт тип кисти, а не id.
internal fun DrawScope.drawStrokeLayers(
    visibleStrokes: List<Stroke>,
    selectedStrokeIds: Set<Long>,
    isDraggingNow: Boolean,
    selectionDragDx: Double,
    selectionDragDy: Double,
    pathCache: StrokeGeometryCache,
    draggedStrokesBuffer: MutableList<Stroke>,
    visibleWorldBounds: WorldBounds? = null
) {
    if (!isDraggingNow) {
        for (i in visibleStrokes.indices) {
            val stroke = visibleStrokes[i]
            if (stroke.brush.type == BrushType.HIGHLIGHTER) {
                drawCachedGeometry(pathCache.getOrBuild(stroke), visibleWorldBounds)
            }
        }
        for (i in visibleStrokes.indices) {
            val stroke = visibleStrokes[i]
            if (stroke.brush.type != BrushType.HIGHLIGHTER) {
                drawCachedGeometry(pathCache.getOrBuild(stroke), visibleWorldBounds)
            }
        }
        return
    }

    // При драге выделенные штрихи вынимаются из общего прохода и рисуются последними,
    // иначе они были бы под невыделенными соседями.
    draggedStrokesBuffer.clear()
    for (i in visibleStrokes.indices) {
        val stroke = visibleStrokes[i]
        if (stroke.id in selectedStrokeIds) {
            draggedStrokesBuffer.add(stroke)
        } else if (stroke.brush.type == BrushType.HIGHLIGHTER) {
            drawCachedGeometry(pathCache.getOrBuild(stroke), visibleWorldBounds)
        }
    }
    for (i in visibleStrokes.indices) {
        val stroke = visibleStrokes[i]
        if (stroke.id !in selectedStrokeIds && stroke.brush.type != BrushType.HIGHLIGHTER) {
            drawCachedGeometry(pathCache.getOrBuild(stroke), visibleWorldBounds)
        }
    }

    if (draggedStrokesBuffer.isNotEmpty()) {
        // Смещаем видимые границы на вектор перетаскивания, чтобы сегменты
        // внутри перетаскиваемых штрихов корректно отсекались относительно экрана
        // Минус, потому что внутри translate координаты локальные: экранные границы возвращаем
        // в систему до сдвига, иначе сегменты на краю пропадали бы во время драга.
        val shiftedVisibleBounds = visibleWorldBounds?.let { b ->
            WorldBounds(
                minX = b.minX - selectionDragDx,
                minY = b.minY - selectionDragDy,
                maxX = b.maxX - selectionDragDx,
                maxY = b.maxY - selectionDragDy
            )
        }

        withTransform({
            translate(selectionDragDx.toFloat(), selectionDragDy.toFloat())
        }) {
            for (i in draggedStrokesBuffer.indices) {
                val stroke = draggedStrokesBuffer[i]
                if (stroke.brush.type == BrushType.HIGHLIGHTER) {
                    drawCachedGeometry(pathCache.getOrBuild(stroke), shiftedVisibleBounds)
                }
            }
            for (i in draggedStrokesBuffer.indices) {
                val stroke = draggedStrokesBuffer[i]
                if (stroke.brush.type != BrushType.HIGHLIGHTER) {
                    drawCachedGeometry(pathCache.getOrBuild(stroke), shiftedVisibleBounds)
                }
            }
        }
    }
}
