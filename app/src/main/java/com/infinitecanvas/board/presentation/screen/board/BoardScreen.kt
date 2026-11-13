package com.infinitecanvas.board.presentation.screen.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinitecanvas.board.R
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.presentation.canvas.InfiniteCanvasView
import kotlin.math.roundToInt

/**
 * Основной экран бесконечной доски с плавающей панелью 3 кистей, ластика и Undo.
 */
@Composable
fun BoardScreen(
    viewModel: BoardViewModel = viewModel()
) {
    val viewportState by viewModel.viewportState.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val activeBrush by viewModel.activeBrush.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()

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

        // Левая плавающая панель инструментов (3 кисти + Ластик + Undo)
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FilterChip(
                    selected = activeTool == CanvasTool.BRUSH && activeBrush.type == BrushType.BALLPOINT,
                    onClick = { viewModel.selectBrushType(BrushType.BALLPOINT) },
                    modifier = Modifier.defaultMinSize(minWidth = 96.dp, minHeight = 48.dp),
                    label = { Text(stringResource(R.string.brush_ballpoint)) }
                )
                FilterChip(
                    selected = activeTool == CanvasTool.BRUSH && activeBrush.type == BrushType.FOUNTAIN,
                    onClick = { viewModel.selectBrushType(BrushType.FOUNTAIN) },
                    modifier = Modifier.defaultMinSize(minWidth = 96.dp, minHeight = 48.dp),
                    label = { Text(stringResource(R.string.brush_fountain)) }
                )
                FilterChip(
                    selected = activeTool == CanvasTool.BRUSH && activeBrush.type == BrushType.PENCIL,
                    onClick = { viewModel.selectBrushType(BrushType.PENCIL) },
                    modifier = Modifier.defaultMinSize(minWidth = 96.dp, minHeight = 48.dp),
                    label = { Text(stringResource(R.string.brush_pencil)) }
                )

                HorizontalDivider(modifier = Modifier.width(72.dp))

                FilterChip(
                    selected = activeTool == CanvasTool.ERASER,
                    onClick = { viewModel.selectEraser() },
                    modifier = Modifier.defaultMinSize(minWidth = 96.dp, minHeight = 48.dp),
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

        // Правый нижний HUD масштаба и счётчика штрихов
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val zoomPercent = (viewportState.scale * 100).roundToInt()
                Text(
                    text = stringResource(R.string.hud_status_format, zoomPercent, strokes.size),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = { viewModel.resetViewport() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.action_reset_zoom))
                }
            }
        }
    }
}