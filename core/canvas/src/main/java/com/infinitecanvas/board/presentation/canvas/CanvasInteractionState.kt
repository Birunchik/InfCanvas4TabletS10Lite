package com.infinitecanvas.board.presentation.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import com.infinitecanvas.board.domain.model.StrokePoint

/**
 * Инкапсулирует временное интерактивное состояние жестов (точки активного штриха,
 * точки лассо, путь лассо, смещение перетаскивания выделения).
 */
internal class CanvasInteractionState {
    val activePoints = mutableStateListOf<StrokePoint>()
    val lassoPoints = mutableStateListOf<StrokePoint>()
    val lassoWorldPath = Path()

    var selectionDragDx by mutableDoubleStateOf(0.0)
        internal set
    var selectionDragDy by mutableDoubleStateOf(0.0)
        internal set

    fun reset() {
        activePoints.clear()
        lassoPoints.clear()
        lassoWorldPath.reset()
        selectionDragDx = 0.0
        selectionDragDy = 0.0
    }
}

@Composable
internal fun rememberCanvasInteractionState(): CanvasInteractionState =
    remember { CanvasInteractionState() }
