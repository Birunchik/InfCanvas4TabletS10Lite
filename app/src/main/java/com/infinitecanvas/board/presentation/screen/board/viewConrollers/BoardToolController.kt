package com.infinitecanvas.board.presentation.screen.board.viewConrollers

import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.CanvasTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.round

class BoardToolController(
    private val onClearSelectionRequested: () -> Unit
) {
    private val _activeTool = MutableStateFlow(CanvasTool.BRUSH)
    val activeTool: StateFlow<CanvasTool> = _activeTool.asStateFlow()

    private val _activeBrush = MutableStateFlow(
        Brush(
            type = BrushType.BALLPOINT,
            colorArgb = 0xFF1A1C1E.toInt(),
            baseSize = 4f,
            opacity = 1f
        )
    )
    val activeBrush: StateFlow<Brush> = _activeBrush.asStateFlow()

    private val _presetColors = MutableStateFlow(
        listOf(
            0xFF1A1C1E.toInt(),
            0xFFD93025.toInt(),
            0xFF1A73E8.toInt(),
            0xFF1E8E3E.toInt()
        )
    )
    val presetColors: StateFlow<List<Int>> = _presetColors.asStateFlow()

    private val _selectedPresetIndex = MutableStateFlow<Int?>(0)
    val selectedPresetIndex: StateFlow<Int?> = _selectedPresetIndex.asStateFlow()

    fun selectBrushType(type: BrushType) {
        _activeTool.value = CanvasTool.BRUSH
        _activeBrush.update { current ->
            val targetOpacity = if (type == BrushType.HIGHLIGHTER) 0.4f else 1f
            current.copy(
                type = type,
                opacity = targetOpacity
            )
        }
        onClearSelectionRequested()
    }

    fun selectEraser() {
        _activeTool.value = CanvasTool.ERASER
        onClearSelectionRequested()
    }

    fun selectLassoTool() {
        _activeTool.value = CanvasTool.LASSO
        onClearSelectionRequested()
    }

    fun selectColor(colorArgb: Int) {
        _activeBrush.update { it.copy(colorArgb = colorArgb) }
    }

    fun selectPresetIndex(index: Int?) {
        _selectedPresetIndex.value = index
    }

    fun updatePresetColorAt(index: Int, newColorArgb: Int) {
        _presetColors.update { current ->
            if (index in current.indices) {
                current.toMutableList().apply { set(index, newColorArgb) }
            } else {
                current
            }
        }
        _activeBrush.update { it.copy(colorArgb = newColorArgb) }
    }

    fun pinCurrentColor(colorArgb: Int) {
        _presetColors.update { current ->
            if (current.contains(colorArgb)) {
                current
            } else if (current.size < 4) {
                current + colorArgb
            } else {
                listOf(current[0], current[2], current[3], colorArgb)
            }
        }
        val newList = _presetColors.value
        _selectedPresetIndex.value = newList.indexOf(colorArgb).takeIf { it >= 0 }
        _activeBrush.update { it.copy(colorArgb = colorArgb) }
    }

    fun removePresetColor(index: Int) {
        val current = _presetColors.value
        if (current.size <= 1 || index !in current.indices) return
        val updated = current.toMutableList().apply { removeAt(index) }
        _presetColors.value = updated
        if (_selectedPresetIndex.value == index) {
            _selectedPresetIndex.value = null
        }
    }

    fun setSnappedBrushSize(rawSize: Float, step: Float = 2f) {
        val snapped = (round(rawSize / step) * step).coerceIn(2f, 24f)
        _activeBrush.update { it.copy(baseSize = snapped) }
    }
}