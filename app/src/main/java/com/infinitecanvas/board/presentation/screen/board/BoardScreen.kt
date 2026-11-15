package com.infinitecanvas.board.presentation.screen.board

import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinitecanvas.board.R
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.presentation.canvas.InfiniteCanvasView
import kotlin.math.roundToInt

@Composable
fun BoardScreen(
    viewModel: BoardViewModel = viewModel()
) {
    val activeTool by viewModel.activeTool.collectAsState()
    val activeBrush by viewModel.activeBrush.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val presetColors by viewModel.presetColors.collectAsState()
    val selectedPresetIndex by viewModel.selectedPresetIndex.collectAsState()
    val isZoomLocked by viewModel.isZoomLocked.collectAsState()
    val selectedStrokeIds by viewModel.selectedStrokeIds.collectAsState()

    val viewportState by viewModel.viewportState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        CanvasLayer(
            viewModel = viewModel,
            isZoomLocked = isZoomLocked
        )

        BoardTopBar(
            selectedPresetIndex = selectedPresetIndex,
            activeTool = activeTool,
            activeBrush = activeBrush,
            canUndo = canUndo,
            presetColors = presetColors,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 96.dp, top = 0.dp),
            onSelectBrushType = { viewModel.selectBrushType(it) },
            onSelectEraser = { viewModel.selectEraser() },
            onSelectLasso = { viewModel.selectLassoTool() },
            onUndo = { viewModel.undo() },
            onSetBrushSize = { viewModel.setSnappedBrushSize(it, step = 2f) },
            onSelectColor = { viewModel.selectColor(it) },
            onSelectPresetIndex = { viewModel.selectPresetIndex(it) },
            onRemovePresetColor = { viewModel.removePresetColor(it) },
            onUpdatePresetColor = { index, color -> viewModel.updatePresetColorAt(index, color) },
            onPinColor = { viewModel.pinCurrentColor(it) }
        )

        BoardBottomHud(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            zoom = viewportState.scale,
            isZoomLocked = isZoomLocked,
            selectedCount = selectedStrokeIds.size,
            onDeleteSelected = { viewModel.deleteSelectedStrokes() },
            onClearSelection = { viewModel.clearSelection() },
            onToggleZoomLock = { viewModel.toggleZoomLock() },
            onResetZoom = viewModel::resetViewport
        )
    }
}
@Composable
private fun CanvasLayer(
    viewModel: BoardViewModel,
    isZoomLocked: Boolean
) {
    val viewportState by viewModel.viewportState.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val activeBrush by viewModel.activeBrush.collectAsState()
    val selectedStrokeIds by viewModel.selectedStrokeIds.collectAsState()

    InfiniteCanvasView(
        isZoomLocked = isZoomLocked,
        viewportState = viewportState,
        strokes = strokes,
        activeTool = activeTool,
        activeBrush = activeBrush,
        selectedStrokeIds = selectedStrokeIds,
        allowFingerDrawing = false,
        onStrokeCommitted = viewModel::onStrokeCommitted,
        onEraseStrokes = viewModel::eraseStrokesByIds,
        onStrokesSelected = viewModel::selectStrokes,
        onMoveSelectedStrokes = viewModel::moveSelectedStrokes,
        onTransform = viewModel::onTransform
    )
}

@Composable
private fun BoardTopBar(
    activeTool: CanvasTool,
    activeBrush: Brush,
    canUndo: Boolean,
    presetColors: List<Int>,
    selectedPresetIndex: Int?,
    // callbacks
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
    modifier: Modifier = Modifier
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
            BrushSelector(
                activeTool = activeTool,
                activeBrush = activeBrush,
                onSelectBrushType = onSelectBrushType
            )

            HorizontalDivider(
                modifier = Modifier.width(1.dp).height(32.dp),
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
                modifier = Modifier.width(1.dp).height(32.dp),
                thickness = DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            BrushSizeSlider(
                currentSize = activeBrush.baseSize,
                onSizeChanged = onSetBrushSize
            )

            HorizontalDivider(
                modifier = Modifier.width(1.dp).height(32.dp),
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
                onClick = onSelectLasso, // прокиньте viewModel::selectLassoTool
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
private fun ColorPickerSection(
    selectedPresetIndex: Int?,
    activeBrushColor: Int,
    presetColors: List<Int>,
    onSelectColor: (Int) -> Unit,
    onSelectPresetIndex: (Int?) -> Unit,
    onRemovePresetColor: (Int) -> Unit,
    onUpdatePresetColor: (Int, Int) -> Unit,
    onPinColor: (Int) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presetColors.forEachIndexed { index, colorInt ->
                val isSelected = activeBrushColor == colorInt
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt))
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier
                        )
                        .combinedClickable(
                            onClick = {
                                if (isSelected) {
                                    onSelectPresetIndex(index)
                                    menuExpanded = true
                                } else {
                                    onSelectColor(colorInt)
                                    onSelectPresetIndex(index)
                                }
                            },
                            onDoubleClick = {
                                if (presetColors.size > 1) {
                                    onRemovePresetColor(index)
                                }
                            }
                        )
                )
            }

            IconButton(
                onClick = {
                    onSelectPresetIndex(null)
                    menuExpanded = true
                },
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(activeBrushColor))
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Палитра",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier
                .width(260.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(12.dp)
        ) {
            MiniatureColorPickerContent(
                initialColorInt = activeBrushColor,
                onColorChanged = { colorInt ->
                    if (selectedPresetIndex != null) {
                        onUpdatePresetColor(selectedPresetIndex, colorInt)
                    } else {
                        onSelectColor(colorInt)
                    }
                },
                onPinColor = { colorInt ->
                    if (selectedPresetIndex == null) {
                        onPinColor(colorInt)
                    }
                    menuExpanded = false
                }
            )
        }
    }
}
@Composable
fun MiniatureColorPickerContent(
    initialColorInt: Int,
    onColorChanged: (Int) -> Unit,
    onPinColor: (Int) -> Unit
) {
    // Конвертируем начальный цвет в HSV один раз при инициализации
    val initialHsv = remember(initialColorInt) {
        FloatArray(3).apply { AndroidColor.colorToHSV(initialColorInt, this) }
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    // Если внешний цвет изменился извне (не во время активного драга), синхронизируем
    LaunchedEffect(initialColorInt) {
        val currentHsv = FloatArray(3).apply { AndroidColor.colorToHSV(initialColorInt, this) }
        // Обновляем только если цвета реально отличаются, чтобы не сбивать жест
        val calculatedInt = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
        if (calculatedInt != initialColorInt) {
            hue = currentHsv[0]
            saturation = currentHsv[1]
            value = currentHsv[2]
        }
    }

    val currentInt = remember(hue, saturation, value) {
        AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // SV-квадрат (объединяем логику тапа и драга безопасно)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            saturation = (offset.x / size.width).coerceIn(0f, 1f)
                            value = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                            onColorChanged(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                        },
                        onDragEnd = {
                            onColorChanged(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                        }
                    ) { change, _ ->
                        saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                        value = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                        onColorChanged(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                val horizontalShader = LinearGradient(
                    0f, 0f, width, 0f,
                    intArrayOf(AndroidColor.WHITE, AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))),
                    null, Shader.TileMode.CLAMP
                )
                drawRect(brush = ShaderBrush(horizontalShader))

                val verticalShader = LinearGradient(
                    0f, 0f, 0f, height,
                    intArrayOf(AndroidColor.TRANSPARENT, AndroidColor.BLACK),
                    null, Shader.TileMode.CLAMP
                )
                drawRect(brush = ShaderBrush(verticalShader))

                val markerX = saturation * width
                val markerY = (1f - value) * height // Исправлена опечатка

                drawCircle(color = Color.White, radius = 7f, center = Offset(markerX, markerY))
                drawCircle(color = Color.Black, radius = 5f, center = Offset(markerX, markerY))
            }
        }

        // Ползунок оттенка (Hue)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val rainbowColors = intArrayOf(
                    AndroidColor.RED, AndroidColor.YELLOW, AndroidColor.GREEN,
                    AndroidColor.CYAN, AndroidColor.BLUE, AndroidColor.MAGENTA, AndroidColor.RED
                )
                val shader = LinearGradient(
                    0f, 0f, width, 0f,
                    rainbowColors, null, Shader.TileMode.CLAMP
                )
                drawRect(brush = ShaderBrush(shader))
            }

            Slider(
                value = hue,
                onValueChange = { newHue ->
                    hue = newHue
                    onColorChanged(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                },
                valueRange = 0f..360f,
                modifier = Modifier.fillMaxSize()
            )
        }

        FilledTonalButton(
            onClick = { onPinColor(currentInt) },
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            Text("Закрепить цвет", style = MaterialTheme.typography.labelMedium)
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
private fun BoardBottomHud(
    zoom: Double,
    isZoomLocked: Boolean,
    selectedCount: Int,
    onToggleZoomLock: () -> Unit,
    onResetZoom: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Кнопки управления выделением (показываются только при активном выделении)
            if (selectedCount > 0) {
                IconButton(
                    onClick = onDeleteSelected,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete_selected),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_clear_selection)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp),
                    thickness = DividerDefaults.Thickness,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            val zoomPercent = (zoom * 100).roundToInt()
            Text(
                text = stringResource(R.string.hud_status_format, zoomPercent),
                style = MaterialTheme.typography.labelLarge
            )

            // Кнопка блокировки зума
            IconButton(
                onClick = onToggleZoomLock,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isZoomLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isZoomLocked) "Зум заблокирован" else "Зум разблокирован",
                    tint = if (isZoomLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            FilledTonalButton(
                onClick = onResetZoom,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(stringResource(R.string.action_reset_zoom))
            }
        }
    }
}

