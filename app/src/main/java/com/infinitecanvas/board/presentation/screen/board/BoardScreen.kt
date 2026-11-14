package com.infinitecanvas.board.presentation.screen.board

import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.presentation.canvas.InfiniteCanvasView
import kotlin.math.roundToInt

@Composable
fun BoardScreen(
    viewModel: BoardViewModel = viewModel()
) {
    val viewportState by viewModel.viewportState.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val activeBrush by viewModel.activeBrush.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val presetColors by viewModel.presetColors.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        InfiniteCanvasView(
            viewportState = viewportState,
            strokes = strokes,
            activeTool = activeTool,
            activeBrush = activeBrush,
            allowFingerDrawing = false,
            onStrokeCommitted = viewModel::onStrokeCommitted,
            onEraseStrokes = viewModel::eraseStrokesByIds,
            onTransform = viewModel::onTransform
        )

        BoardTopBar(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 24.dp, top = 20.dp),
            viewModel = viewModel,
            activeTool = activeTool,
            activeBrush = activeBrush,
            canUndo = canUndo,
            presetColors = presetColors
        )

        BoardBottomHud(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            zoom = viewportState.scale,
            strokesCount = strokes.size,
            onResetZoom = viewModel::resetViewport
        )
    }
}
@Composable
private fun BoardTopBar(
    viewModel: BoardViewModel,
    activeTool: CanvasTool,
    activeBrush: com.infinitecanvas.board.domain.model.Brush,
    canUndo: Boolean,
    presetColors: List<Int>,
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
            BrushSelector(activeTool = activeTool, activeBrush = activeBrush, viewModel = viewModel)

            HorizontalDivider(
                modifier = Modifier.width(1.dp).height(32.dp),
                thickness = DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            ColorPickerSection(activeBrushColor = activeBrush.colorArgb, presetColors = presetColors, viewModel = viewModel)

            HorizontalDivider(
                modifier = Modifier.width(1.dp).height(32.dp),
                thickness = DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            BrushSizeSlider(
                currentSize = activeBrush.baseSize,
                onSizeChanged = { viewModel.setSnappedBrushSize(it, step = 4f) }
            )

            HorizontalDivider(
                modifier = Modifier.width(1.dp).height(32.dp),
                thickness = DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            FilterChip(
                selected = activeTool == CanvasTool.ERASER,
                onClick = { viewModel.selectEraser() },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                label = { Text(stringResource(R.string.tool_eraser)) }
            )

            IconButton(
                onClick = { viewModel.undo() },
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
    activeBrush: com.infinitecanvas.board.domain.model.Brush,
    viewModel: BoardViewModel
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            BrushType.BALLPOINT to R.string.brush_ballpoint,
            BrushType.FOUNTAIN to R.string.brush_fountain,
            BrushType.PENCIL to R.string.brush_pencil
        ).forEach { (type, labelRes) ->
            FilterChip(
                selected = activeTool == CanvasTool.BRUSH && activeBrush.type == type,
                onClick = { viewModel.selectBrushType(type) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

@Composable
private fun ColorPickerSection(
    activeBrushColor: Int,
    presetColors: List<Int>,
    viewModel: BoardViewModel
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presetColors.forEach { colorInt ->
                val isSelected = activeBrushColor == colorInt
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 28.dp else 24.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt))
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier
                        )
                        .clickable {
                            if (isSelected) menuExpanded = true
                            else viewModel.selectColor(colorInt)
                        }
                )
            }

            IconButton(
                onClick = { menuExpanded = true },
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
                onColorChanged = { viewModel.selectColor(it) },
                onPinColor = {
                    viewModel.pinCurrentColor(it)
                    menuExpanded = false
                }
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
            valueRange = 2f..24f,
            steps = 5,
            modifier = Modifier.height(32.dp)
        )
    }
}

@Composable
private fun BoardBottomHud(
    zoom: Double,
    strokesCount: Int,
    onResetZoom: () -> Unit,
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            val zoomPercent = (zoom * 100).roundToInt()
            Text(
                text = stringResource(R.string.hud_status_format, zoomPercent, strokesCount),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalButton(
                onClick = onResetZoom,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(stringResource(R.string.action_reset_zoom))
            }
        }
    }
}

@Composable
fun MiniatureColorPickerContent(
    initialColorInt: Int,
    onColorChanged: (Int) -> Unit,
    onPinColor: (Int) -> Unit
) {
    val hsv = FloatArray(3).apply { AndroidColor.colorToHSV(initialColorInt, this) }

    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    val currentInt = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // SV-квадрат
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(hue) {
                    detectTapGestures { offset ->
                        saturation = (offset.x / size.width).coerceIn(0f, 1f)
                        value = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                        onColorChanged(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                    }
                }
                .pointerInput(hue) {
                    detectDragGestures { change, _ ->
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
                val markerY = (1f - value) * height

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