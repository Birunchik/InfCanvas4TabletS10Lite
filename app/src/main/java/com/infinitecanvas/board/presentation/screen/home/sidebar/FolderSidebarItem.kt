package com.infinitecanvas.board.presentation.screen.home.sidebar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.R
import com.infinitecanvas.board.domain.model.Folder

/**
 * Один элемент списка папок в sidebar: иконка + имя + overflow-меню.
 *
 * isSelected приходит снаружи, а не вычисляется здесь: сравнение с
 * FolderFilter.Custom — это про фильтр, а не про папку. Item не должен
 * знать, как устроен FolderFilter.
 *
 * menuOpen — remember внутри item'а. С key = { it.id } в LazyColumn state
 * привязан к id: при удалении соседней папки Compose не переиспользует
 * открытое меню этой.
 */
@Composable
internal fun FolderSidebarItem(
    folder: Folder,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    NavigationDrawerItem(
        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
        label = {
            Text(
                text = folder.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        badge = {
            // Box нужен, чтобы DropdownMenu позиционировался относительно IconButton,
            // а не относительно всего NavigationDrawerItem
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_folder_options),
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = {
                            // Сбрасываем флаг до колбэка: иначе меню висит поверх открывшегося диалога
                            menuOpen = false
                            onRequestRename()
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
                            menuOpen = false
                            onRequestDelete()
                        }
                    )
                }
            }
        },
        selected = isSelected,
        onClick = onSelect,
        modifier = modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}