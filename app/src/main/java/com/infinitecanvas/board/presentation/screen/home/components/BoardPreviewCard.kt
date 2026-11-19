package com.infinitecanvas.board.presentation.screen.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.infinitecanvas.board.R
import com.infinitecanvas.board.domain.model.BoardSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BoardPreviewCard(
    board: BoardSummary,
    folderName: String?,
    onOpenBoard: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestMove: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // remember по updatedAt: пересобираем форматтер только при смене даты, а не на каждый рекомпоз
    val formattedDate = remember(board.updatedAt) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(board.updatedAt))
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenBoard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            // Область превью содержимого доски (соотношение 16:10 как у Tab S10 Lite)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .background(Color(0xFFF8F9FA)),
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

            // Нижняя информационная часть карточки
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = board.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = if (folderName != null) {
                        "$formattedDate • $folderName"
                    } else {
                        formattedDate
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_board_options)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            onClick = {
                                // Сбрасываем флаг до колбэка: иначе меню висит поверх открывшегося диалога
                                menuExpanded = false
                                onRequestRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move_to_folder)) },
                            onClick = {
                                menuExpanded = false
                                onRequestMove()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.action_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRequestDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyBoardGridPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Линии считаем от фактического size: готовая фоновая картинка тянулась бы при смене числа колонок
            val step = 28.dp.toPx()
            val lineColor = Color(0xFFE5E8ED)
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
            color = Color(0xFF7A818C)
        )
    }
}