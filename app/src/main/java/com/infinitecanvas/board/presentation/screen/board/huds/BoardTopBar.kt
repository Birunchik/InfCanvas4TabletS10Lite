package com.infinitecanvas.board.presentation.screen.board.huds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.R
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.CanvasTool

@Composable
internal fun BoardTopBar(
    activeTool: CanvasTool,
    activeBrush: Brush,
    canUndo: Boolean,
    presetColors: List<Int>,
    selectedPresetIndex: Int?,
    onSelectBrushType: (BrushType) -> Unit,
    onSelectEraser: () -> Unit,
    onSelectLasso: () -> Unit,
    onUndo: () -> Unit,
    onSetBrushSize: (Float) -> Unit,
    onSelectColor: (Int) -> Unit,
    onSelectPresetIndex: (Int?) -> Unit,
    onRemovePresetColor: (Int) -> Unit,
    onUpdatePresetColor: (Int, Int) -> Unit,
    onPinColor: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back_to_boards)
                )
            }

            BrushSelector(
                activeTool = activeTool,
                activeBrush = activeBrush,
                onSelectBrushType = onSelectBrushType
            )

            HorizontalDivider(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp),
                thickness = DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            ColorPickerSection(
                selectedPresetIndex = selectedPresetIndex,
                activeBrushColor = activeBrush.colorArgb,
                presetColors = presetColors,
                onSelectColor = onSelectColor,
                onSelectPresetIndex = onSelectPresetIndex,
                onRemovePresetColor = onRemovePresetColor,
                onUpdatePresetColor = onUpdatePresetColor,
                onPinColor = onPinColor
            )

            HorizontalDivider(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp),
                thickness = DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            BrushSizeSlider(
                currentSize = activeBrush.baseSize,
                onSizeChanged = onSetBrushSize
            )

            HorizontalDivider(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp),
                thickness = DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            FilterChip(
                selected = activeTool == CanvasTool.ERASER,
                onClick = onSelectEraser,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                label = { Text(stringResource(R.string.tool_eraser)) }
            )

            FilterChip(
                selected = activeTool == CanvasTool.LASSO,
                onClick = onSelectLasso,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                label = { Text(stringResource(R.string.tool_lasso)) }
            )

            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.action_undo)
                )
            }
        }
    }
}

@Composable
private fun BrushSelector(
    activeTool: CanvasTool,
    activeBrush: Brush,
    onSelectBrushType: (BrushType) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            BrushType.BALLPOINT to R.string.brush_ballpoint,
            BrushType.FOUNTAIN to R.string.brush_fountain,
            BrushType.PENCIL to R.string.brush_pencil
        ).forEach { (type, labelRes) ->
            FilterChip(
                selected = activeTool == CanvasTool.BRUSH && activeBrush.type == type,
                onClick = { onSelectBrushType(type) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

@Composable
private fun BrushSizeSlider(
    currentSize: Float,
    onSizeChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier.width(130.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Размер: ${currentSize.toInt()}px",
            style = MaterialTheme.typography.labelSmall
        )
        Slider(
            value = currentSize,
            onValueChange = onSizeChanged,
            valueRange = 0f..12f,
            steps = 5,
            modifier = Modifier.height(32.dp)
        )
    }
}