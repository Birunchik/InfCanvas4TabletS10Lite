package com.infinitecanvas.board.presentation.screen.home.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.infinitecanvas.feature.home.R
import com.infinitecanvas.board.domain.model.BoardSummary
import java.io.File

/**
 * Превью доски: миниатюра из файла либо placeholder-сетка.
 *
 * Высота задаётся aspectRatio(1.6f) — родитель (BoardPreviewCard) не управляет
 * высотой, поэтому превью одинакового размера независимо от длины заголовка.
 */
@Composable
internal fun BoardThumbnail(
    board: BoardSummary,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewBackground = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .background(previewBackground),
        contentAlignment = Alignment.Center
    ) {
        val thumbFile = remember(board.thumbnailPath) {
            board.thumbnailPath?.let { File(it) }?.takeIf { it.exists() }
        }

        if (thumbFile != null) {
            // Ключ кэша включает updatedAt: без него Coil отдаст старую миниатюру после перерисовки доски
            val imageRequest = remember(thumbFile, board.updatedAt) {
                ImageRequest.Builder(context)
                    .data(thumbFile)
                    .memoryCacheKey("board_thumb_${board.id}_${board.updatedAt}")
                    .diskCacheKey("board_thumb_${board.id}_${board.updatedAt}")
                    .size(480, 300)
                    .crossfade(150)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = board.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            EmptyBoardGridPlaceholder()
        }
    }
}

@Composable
private fun EmptyBoardGridPlaceholder() {
    // Читаем цвета ДО Canvas: DrawScope не Composable, MaterialTheme внутри недоступен
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Линии считаем от фактического size: готовая фоновая картинка тянулась бы при смене числа колонок
            val step = 28.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
        Text(
            text = stringResource(R.string.board_empty_preview),
            style = MaterialTheme.typography.labelMedium,
            color = labelColor
        )
    }
}