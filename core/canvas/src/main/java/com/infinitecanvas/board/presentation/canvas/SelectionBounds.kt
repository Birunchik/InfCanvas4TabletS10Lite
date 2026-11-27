package com.infinitecanvas.board.presentation.canvas

import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.WorldBounds

// Padding в world px: рамка должна масштабироваться вместе с холстом, а не оставаться 8 px на экране.
private const val SELECTION_PADDING = 8.0

/**
 * World-space bounding box выбранных штрихов с небольшим визуальным padding'ом.
 * Возвращает null, если ни один из [selectedIds] не найден в [strokes].
 */
fun computeSelectionWorldBounds(
    strokes: List<Stroke>,
    selectedIds: Set<Long>
): WorldBounds? {
    if (selectedIds.isEmpty()) return null

    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    var found = false

    for (i in strokes.indices) {
        val s = strokes[i]
        if (s.id !in selectedIds) continue
        val b = s.bounds
        if (b.minX < minX) minX = b.minX
        if (b.minY < minY) minY = b.minY
        if (b.maxX > maxX) maxX = b.maxX
        if (b.maxY > maxY) maxY = b.maxY
        found = true
    }
    if (!found) return null
    // Те же bounds используются для hit-test драга: padding расширяет зону захвата, а не только рамку.
    return WorldBounds(
        minX = minX - SELECTION_PADDING,
        minY = minY - SELECTION_PADDING,
        maxX = maxX + SELECTION_PADDING,
        maxY = maxY + SELECTION_PADDING
    )
}

/**
 * Вариант для уже построенного индекса id → bounds.
 *
 * Смысл — не перебирать весь список штрихов: сложность зависит от числа ВЫДЕЛЕННЫХ
 * объектов, а не от общего размера доски. При 10k штрихов и клике по одному объекту
 * list-версия делала 10k итераций, эта — одну.
 */
fun computeSelectionWorldBounds(
    selectedIds: Set<Long>,
    boundsById: Map<Long, WorldBounds>
): WorldBounds? {
    if (selectedIds.isEmpty()) return null

    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    var found = false

    for (id in selectedIds) {
        val b = boundsById[id] ?: continue
        if (b.minX < minX) minX = b.minX
        if (b.minY < minY) minY = b.minY
        if (b.maxX > maxX) maxX = b.maxX
        if (b.maxY > maxY) maxY = b.maxY
        found = true
    }
    if (!found) return null
    return WorldBounds(
        minX = minX - SELECTION_PADDING,
        minY = minY - SELECTION_PADDING,
        maxX = maxX + SELECTION_PADDING,
        maxY = maxY + SELECTION_PADDING
    )
}