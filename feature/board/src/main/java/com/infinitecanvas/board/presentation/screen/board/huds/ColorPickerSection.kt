package com.infinitecanvas.board.presentation.screen.board.huds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitecanvas.feature.board.R

@Composable
internal fun ColorPickerSection(
    selectedPresetIndex: Int?,
    activeBrushColor: Int,
    presetColors: List<Int>,
    onSelectColor: (Int) -> Unit,
    onSelectPresetIndex: (Int?) -> Unit,
    onRemovePresetColor: (Int) -> Unit,
    onUpdatePresetColor: (Int, Int) -> Unit,
    onPinColor: (Int) -> Unit
) {
    // selectedPresetIndex — это ещё и режим редактирования: клик по уже выбранному
    // цвету открывает палитру, и правки пишутся в пресет, а не в кисть.
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
                        .size(if (isSelected) 28.dp else 24.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt))
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        // Двойной клик удаляет цвет (если пресетов >1), одиночный по выбранному открывает
                        // редактор — поэтому удаление не повешено на долгое нажатие.
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
                        contentDescription = stringResource(R.string.action_open_palette),
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
                .width(276.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Пиннить можно только новый цвет, удалять — только существующий и не последний;
            // кнопки именно неактивны, а не скрыты, чтобы меню не меняло высоту.
            val canPin = selectedPresetIndex == null
            val canDelete = selectedPresetIndex != null && presetColors.size > 1

            MiniatureColorPickerContent(
                initialColorInt = activeBrushColor,
                canPin = canPin,
                canDelete = canDelete,
                onColorChanged = { colorInt ->
                    if (selectedPresetIndex != null) {
                        onUpdatePresetColor(selectedPresetIndex, colorInt)
                    } else {
                        onSelectColor(colorInt)
                    }
                },
                onPinColor = { colorInt ->
                    if (canPin) {
                        onPinColor(colorInt)
                    }
                    menuExpanded = false
                },
                onDeleteColor = {
                    if (canDelete) {
                        onRemovePresetColor(selectedPresetIndex)
                        onSelectPresetIndex(null)
                    }
                    menuExpanded = false
                }
            )
        }
    }
}