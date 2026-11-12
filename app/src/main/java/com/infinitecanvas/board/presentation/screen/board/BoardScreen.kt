package com.infinitecanvas.board.presentation.screen.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinitecanvas.board.presentation.canvas.InfiniteCanvasView
import kotlin.math.roundToInt

@Composable
fun BoardScreen(
    viewModel: BoardViewModel = viewModel()
) {
    val viewportState by viewModel.viewportState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Бесконечный холст на весь экран
        InfiniteCanvasView(
            viewportState = viewportState,
            onTransform = viewModel::onTransform
        )

        // Плавающий HUD в правом нижнем углу с учетом системных отступов Android 16
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val zoomPercent = (viewportState.scale * 100).roundToInt()
                Text(
                    text = "Зум: $zoomPercent% | X: ${viewportState.offsetX.roundToInt()}, Y: ${viewportState.offsetY.roundToInt()}",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = { viewModel.resetViewport() }
                ) {
                    Text("100%")
                }
            }
        }
    }
}