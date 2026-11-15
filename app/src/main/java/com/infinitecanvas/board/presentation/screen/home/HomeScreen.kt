package com.infinitecanvas.board.presentation.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.R
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Folder
import com.infinitecanvas.board.presentation.screen.home.components.BoardPreviewCard
import com.infinitecanvas.board.presentation.screen.home.components.FolderSidebarContent
import com.infinitecanvas.board.presentation.screen.home.components.MoveToFolderDialog
import com.infinitecanvas.board.presentation.screen.home.components.TextInputDialog
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenBoard: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.openBoardEvents.collect { boardId ->
            onOpenBoard(boardId)
        }
    }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var boardToRename by remember { mutableStateOf<BoardSummary?>(null) }
    var boardToMove by remember { mutableStateOf<BoardSummary?>(null) }

    val folderNamesById = remember(state.folders) {
        state.folders.associate { it.id to it.name }
    }

    // Адаптивный выбор: на 10.9" планшете постоянная панель слева; в узком split-screen — выдвижная шторка
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val usePermanentDrawer = maxWidth >= 600.dp

        if (usePermanentDrawer) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        FolderSidebarContent(
                            folders = state.folders,
                            selectedFilter = state.selectedFilter,
                            onSelectFilter = viewModel::selectFilter,
                            onRequestCreateFolder = { showCreateFolderDialog = true },
                            onRequestRenameFolder = { folderToRename = it },
                            onRequestDeleteFolder = { viewModel.deleteFolder(it.id) }
                        )
                    }
                }
            ) {
                BoardsGridPane(
                    state = state,
                    folderNamesById = folderNamesById,
                    showNavigationIcon = false,
                    onOpenDrawer = {},
                    onSearchChange = viewModel::updateSearchQuery,
                    onCreateBoard = { defaultTitle ->
                        viewModel.createAndOpenBoard(defaultTitle)
                    },
                    onOpenBoard = onOpenBoard,
                    onRequestRenameBoard = { boardToRename = it },
                    onRequestMoveBoard = { boardToMove = it },
                    onDeleteBoard = { viewModel.deleteBoard(it.id) }
                )
            }
        } else {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        FolderSidebarContent(
                            folders = state.folders,
                            selectedFilter = state.selectedFilter,
                            onSelectFilter = { filter ->
                                viewModel.selectFilter(filter)
                                scope.launch { drawerState.close() }
                            },
                            onRequestCreateFolder = { showCreateFolderDialog = true },
                            onRequestRenameFolder = { folderToRename = it },
                            onRequestDeleteFolder = { viewModel.deleteFolder(it.id) }
                        )
                    }
                }
            ) {
                BoardsGridPane(
                    state = state,
                    folderNamesById = folderNamesById,
                    showNavigationIcon = true,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSearchChange = viewModel::updateSearchQuery,
                    onCreateBoard = { defaultTitle ->
                        viewModel.createAndOpenBoard(defaultTitle)
                    },
                    onOpenBoard = onOpenBoard,
                    onRequestRenameBoard = { boardToRename = it },
                    onRequestMoveBoard = { boardToMove = it },
                    onDeleteBoard = { viewModel.deleteBoard(it.id) }
                )
            }
        }
    }

    if (showCreateFolderDialog) {
        TextInputDialog(
            title = stringResource(R.string.dialog_new_folder_title),
            confirmLabel = stringResource(R.string.action_create),
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            }
        )
    }

    folderToRename?.let { folder ->
        TextInputDialog(
            title = stringResource(R.string.dialog_rename_folder_title),
            initialValue = folder.name,
            confirmLabel = stringResource(R.string.action_save),
            onDismiss = { folderToRename = null },
            onConfirm = { newName ->
                viewModel.renameFolder(folder.id, newName)
                folderToRename = null
            }
        )
    }

    boardToRename?.let { board ->
        TextInputDialog(
            title = stringResource(R.string.dialog_rename_board_title),
            initialValue = board.title,
            confirmLabel = stringResource(R.string.action_save),
            onDismiss = { boardToRename = null },
            onConfirm = { newTitle ->
                viewModel.renameBoard(board.id, newTitle)
                boardToRename = null
            }
        )
    }

    boardToMove?.let { board ->
        MoveToFolderDialog(
            folders = state.folders,
            currentFolderId = board.folderId,
            onDismiss = { boardToMove = null },
            onSelectFolder = { targetFolderId ->
                viewModel.moveBoardToFolder(board.id, targetFolderId)
                boardToMove = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardsGridPane(
    state: HomeUiState,
    folderNamesById: Map<Long, String>,
    showNavigationIcon: Boolean,
    onOpenDrawer: () -> Unit,
    onSearchChange: (String) -> Unit,
    onCreateBoard: (defaultTitle: String) -> Unit,
    onOpenBoard: (Long) -> Unit,
    onRequestRenameBoard: (BoardSummary) -> Unit,
    onRequestMoveBoard: (BoardSummary) -> Unit,
    onDeleteBoard: (BoardSummary) -> Unit
) {
    val headerTitle = when (val filter = state.selectedFilter) {
        FolderFilter.All -> stringResource(R.string.folder_all_boards)
        FolderFilter.Unassigned -> stringResource(R.string.folder_unassigned)
        is FolderFilter.Custom -> filter.folderName
    }
    val defaultBoardPrefix = stringResource(R.string.default_board_title)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.search_boards_hint)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.action_clear_search)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .width(280.dp)
                                .padding(end = 16.dp)
                        )
                    }
                },
                navigationIcon = {
                    if (showNavigationIcon) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.action_open_folders)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val nextNumber = state.boards.size + 1
                    onCreateBoard("$defaultBoardPrefix $nextNumber")
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_board)) }
            )
        }
    ) { innerPadding ->
        if (state.boards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.empty_boards_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = stringResource(R.string.empty_boards_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 250.dp),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 88.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = state.boards,
                    key = { it.id },
                    contentType = { "board_card" }
                ) { board ->
                    val folderLabel = if (state.selectedFilter is FolderFilter.All) {
                        board.folderId?.let { folderNamesById[it] }
                    } else {
                        null
                    }
                    BoardPreviewCard(
                        board = board,
                        folderName = folderLabel,
                        onOpenBoard = { onOpenBoard(board.id) },
                        onRequestRename = { onRequestRenameBoard(board) },
                        onRequestMove = { onRequestMoveBoard(board) },
                        onRequestDelete = { onDeleteBoard(board) }
                    )
                }
            }
        }
    }
}