package com.infinitecanvas.board.presentation.screen.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.R
import com.infinitecanvas.board.domain.model.Folder
import com.infinitecanvas.board.presentation.screen.home.FolderFilter

@Composable
internal fun FolderSidebarContent(
    folders: List<Folder>,
    selectedFilter: FolderFilter,
    onSelectFilter: (FolderFilter) -> Unit,
    onRequestCreateFolder: () -> Unit,
    onRequestRenameFolder: (Folder) -> Unit,
    onRequestDeleteFolder: (Folder) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            // Ширина фиксирована: растянутая панель на планшете отъедает место у грида досок
            .width(272.dp)
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
            label = { Text(stringResource(R.string.folder_all_boards)) },
            selected = selectedFilter is FolderFilter.All,
            onClick = { onSelectFilter(FolderFilter.All) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.FolderOff, contentDescription = null) },
            label = { Text(stringResource(R.string.folder_unassigned)) },
            selected = selectedFilter is FolderFilter.Unassigned,
            onClick = { onSelectFilter(FolderFilter.Unassigned) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp))

        Text(
            text = stringResource(R.string.folders_section_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(
                items = folders,
                key = { it.id },
                contentType = { "folder_item" }
            ) { folder ->
                val isSelected = selectedFilter is FolderFilter.Custom &&
                        selectedFilter.folderId == folder.id
                FolderSidebarItem(
                    folder = folder,
                    isSelected = isSelected,
                    onSelect = { onSelectFilter(FolderFilter.Custom(folder.id, folder.name)) },
                    onRequestRename = { onRequestRenameFolder(folder) },
                    onRequestDelete = { onRequestDeleteFolder(folder) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        FilledTonalButton(
            onClick = onRequestCreateFolder,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_create_folder))
        }
    }
}