package com.infinitecanvas.board.presentation.screen.board.huds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitecanvas.feature.board.R
import kotlin.math.roundToInt

@Composable
// Состав кнопок зависит от выделения; штрихи и картинка взаимоисключающие
// (контроллеры сбрасывают чужое выделение), поэтому блоки не дублируются.
internal fun BoardBottomHud(
    zoom: Double,
    isZoomLocked: Boolean,
    selectedCount: Int,
    hasSelectedImage: Boolean,
    hasClipboardContent: Boolean,
    isSelectedImagePinned: Boolean,
    onTogglePinSelectedImage: () -> Unit,
    onToggleZoomLock: () -> Unit,
    onResetZoom: () -> Unit,
    onCopySelected: () -> Unit,
    onPasteClipboard: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDeleteSelectedImage: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (selectedCount > 0) {
                IconButton(
                    onClick = onCopySelected,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy_selected),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

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
            }

            if (hasSelectedImage) {
                IconButton(
                    onClick = onTogglePinSelectedImage,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = if (isSelectedImagePinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isSelectedImagePinned) "Открепить изображение" else "Закрепить поверх экрана",
                        tint = if (isSelectedImagePinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDeleteSelectedImage,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete_image),
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
            }

            if (hasClipboardContent) {
                IconButton(
                    onClick = onPasteClipboard,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.action_paste_clipboard),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (selectedCount > 0 || hasSelectedImage || hasClipboardContent) {
                HorizontalDivider(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(24.dp),
                    thickness = DividerDefaults.Thickness,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            // zoom — множитель (1.0 = 100 %), а не проценты; roundToInt убирает 99 %
            // из-за погрешности Double при пересчёте масштаба.
            val zoomPercent = (zoom * 100).roundToInt()

            IconButton(
                // Lock отключает только масштаб: pan продолжает применяться, поэтому доска
                // остаётся подвижной при заблокированном зуме.
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
                modifier = Modifier.defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
            ) {
                Text(
                    text = "$zoomPercent%",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}