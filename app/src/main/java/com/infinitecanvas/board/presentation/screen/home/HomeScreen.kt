package com.infinitecanvas.board.presentation.screen.home

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.R
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.presentation.screen.home.components.BoardPreviewCard
import com.infinitecanvas.board.presentation.screen.home.components.DeleteConfirmationDialog
import com.infinitecanvas.board.presentation.screen.home.components.EmptyState
import com.infinitecanvas.board.presentation.screen.home.components.FolderSidebarContent
import com.infinitecanvas.board.presentation.screen.home.components.MoveToFolderDialog
import com.infinitecanvas.board.presentation.screen.home.components.TextInputDialog
import kotlinx.coroutines.launch

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenBoard: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

// Ошибки показываем Snackbar'ом: одна за раз, ничего не блокирует.
// LaunchedEffect(viewModel) — не переподписывается на рекомпозициях.
    LaunchedEffect(viewModel) {
        viewModel.errorEvents.collect { error ->
            snackbarHostState.showSnackbar(
                message = context.getString(error.messageResId)
            )
        }
    }

    // collect один раз на жизнь ViewModel: событие разовое, при рекомпозиции повторной подписки не будет
    LaunchedEffect(viewModel) {
        viewModel.openBoardEvents.collect { boardId ->
            onOpenBoard(boardId)
        }
    }

    // Диалоги — локальный remember, а не ViewModel: не переживают поворот намеренно, зато нет «залипших» флагов
    var dialogState by remember { mutableStateOf<HomeDialogState>(HomeDialogState.None) }

    val folderNamesById = remember(state.folders) {
        state.folders.associate { it.id to it.name }
    }

    // Адаптивный выбор: на 10.9" планшете постоянная панель слева; в узком split-screen — выдвижная шторка
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 600.dp — брейкпоинт Material: ниже постоянная панель оставляет гриду меньше двух колонок
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
                            onRequestCreateFolder = { dialogState = HomeDialogState.CreateFolder },
                            onRequestRenameFolder = { dialogState = HomeDialogState.RenameFolder(it) },
                            onRequestDeleteFolder = { dialogState = HomeDialogState.DeleteFolder(it) }
                        )
                    }
                }
            ) {
                BoardsGridPane(
                    state = state,
                    folderNamesById = folderNamesById,
                    onOpenDrawer = null,
                    onSearchChange = viewModel::updateSearchQuery,
                    onCreateBoard = { defaultTitle ->
                        viewModel.createAndOpenBoard(defaultTitle)
                    },
                    onOpenBoard = onOpenBoard,
                    onRequestRenameBoard = { dialogState = HomeDialogState.RenameBoard(it) },
                    onRequestMoveBoard = { dialogState = HomeDialogState.MoveBoard(it) },
                    onDeleteBoard = { dialogState = HomeDialogState.DeleteBoard(it) },
                    snackbarHostState = snackbarHostState
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
                            onRequestCreateFolder = { dialogState = HomeDialogState.CreateFolder },
                            onRequestRenameFolder = { dialogState = HomeDialogState.RenameFolder(it) },
                            onRequestDeleteFolder = { dialogState = HomeDialogState.DeleteFolder(it) }
                        )
                    }
                }
            ) {
                BoardsGridPane(
                    state = state,
                    folderNamesById = folderNamesById,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSearchChange = viewModel::updateSearchQuery,
                    onCreateBoard = { defaultTitle ->
                        viewModel.createAndOpenBoard(defaultTitle)
                    },
                    onOpenBoard = onOpenBoard,
                    onRequestRenameBoard = { dialogState = HomeDialogState.RenameBoard(it) },
                    onRequestMoveBoard = { dialogState = HomeDialogState.MoveBoard(it) },
                    onDeleteBoard = { dialogState = HomeDialogState.DeleteBoard(it) },
                    snackbarHostState = snackbarHostState   // ← новое
                )
            }
        }
    }

    // Один when вместо шести if/let: исчерпывающая проверка компилятором,
// невозможно забыть обработать новое состояние HomeDialogState.
    when (val dialog = dialogState) {
        HomeDialogState.None -> Unit

        HomeDialogState.CreateFolder -> TextInputDialog(
            title = stringResource(R.string.dialog_new_folder_title),
            confirmLabel = stringResource(R.string.action_create),
            onDismiss = { dialogState = HomeDialogState.None },
            onConfirm = { name ->
                viewModel.createFolder(name)
                dialogState = HomeDialogState.None
            }
        )

        is HomeDialogState.RenameFolder -> TextInputDialog(
            title = stringResource(R.string.dialog_rename_folder_title),
            initialValue = dialog.folder.name,
            confirmLabel = stringResource(R.string.action_save),
            onDismiss = { dialogState = HomeDialogState.None },
            onConfirm = { newName ->
                viewModel.renameFolder(dialog.folder.id, newName)
                dialogState = HomeDialogState.None
            }
        )

        is HomeDialogState.RenameBoard -> TextInputDialog(
            title = stringResource(R.string.dialog_rename_board_title),
            initialValue = dialog.board.title,
            confirmLabel = stringResource(R.string.action_save),
            onDismiss = { dialogState = HomeDialogState.None },
            onConfirm = { newTitle ->
                viewModel.renameBoard(dialog.board.id, newTitle)
                dialogState = HomeDialogState.None
            }
        )

        is HomeDialogState.MoveBoard -> MoveToFolderDialog(
            folders = state.folders,
            currentFolderId = dialog.board.folderId,
            onDismiss = { dialogState = HomeDialogState.None },
            onSelectFolder = { targetFolderId ->
                viewModel.moveBoardToFolder(dialog.board.id, targetFolderId)
                dialogState = HomeDialogState.None
            }
        )

        is HomeDialogState.DeleteBoard -> DeleteConfirmationDialog(
            title = stringResource(R.string.dialog_delete_board_title),
            message = stringResource(R.string.dialog_delete_board_message, dialog.board.title),
            onDismiss = { dialogState = HomeDialogState.None },
            onConfirm = {
                viewModel.deleteBoard(dialog.board.id)
                dialogState = HomeDialogState.None
            }
        )

        is HomeDialogState.DeleteFolder -> DeleteConfirmationDialog(
            title = stringResource(R.string.dialog_delete_folder_title),
            message = stringResource(
                R.string.dialog_delete_folder_message,
                dialog.folder.name,
                stringResource(R.string.folder_unassigned)
            ),
            onDismiss = { dialogState = HomeDialogState.None },
            onConfirm = {
                viewModel.deleteFolder(dialog.folder.id)
                dialogState = HomeDialogState.None
            }
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardsTopBar(
    title: String,
    searchQuery: String,
    onOpenDrawer: (() -> Unit)?,
    onSearchChange: (String) -> Unit
) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_boards_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
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
            if (onOpenDrawer != null){
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
}

@Composable
private fun BoardsGrid(
    boards: List<BoardSummary>,
    folderNamesById: Map<Long, String>,
    showFolderLabel: Boolean,
    contentPadding: PaddingValues,
    onOpenBoard: (Long) -> Unit,
    onRequestRename: (BoardSummary) -> Unit,
    onRequestMove: (BoardSummary) -> Unit,
    onDelete: (BoardSummary) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 250.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = boards,
            // key = id обязателен: иначе при удалении или фильтрации Compose переиспользует состояние чужих карточек
            key = { it.id },
            contentType = { "board_card" }
        ) { board ->
            // Метку папки показываем только в «Все доски»: внутри папки она дублировала бы заголовок
            val folderLabel = if (showFolderLabel) {
                board.folderId?.let { folderNamesById[it] }
            } else {
                null
            }
            BoardPreviewCard(
                board = board,
                folderName = folderLabel,
                onOpenBoard = { onOpenBoard(board.id) },
                onRequestRename = { onRequestRename(board) },
                onRequestMove = { onRequestMove(board) },
                onRequestDelete = { onDelete(board) }
            )
        }
    }
}

@Composable
private fun BoardsEmptyState(
    searchQuery: String,
    filter: FolderFilter,
    contentPadding: PaddingValues
) {
    // Порядок веток важен: поиск проверяем ПЕРВЫМ. Если пользователь ищет
    // внутри папки и ничего не нашёл — «В этой папке пусто» было бы ложью:
    // папка не пуста, просто запрос не совпал.
    when {
        searchQuery.isNotBlank() -> EmptyState(
            titleRes = R.string.empty_search_title,
            subtitleRes = R.string.empty_search_subtitle,
            modifier = Modifier.padding(contentPadding)
        )

        filter is FolderFilter.Custom -> EmptyState(
            titleRes = R.string.empty_folder_title,
            subtitleRes = R.string.empty_boards_subtitle,
            modifier = Modifier.padding(contentPadding)
        )

        else -> EmptyState(
            titleRes = R.string.empty_boards_title,
            subtitleRes = R.string.empty_boards_subtitle,
            modifier = Modifier.padding(contentPadding)
        )
    }
}

@Composable
private fun BoardsGridPane(
    state: HomeUiState,
    folderNamesById: Map<Long, String>,
    onOpenDrawer: (() -> Unit)?,
    onSearchChange: (String) -> Unit,
    onCreateBoard: (defaultTitle: String) -> Unit,
    onOpenBoard: (Long) -> Unit,
    onRequestRenameBoard: (BoardSummary) -> Unit,
    onRequestMoveBoard: (BoardSummary) -> Unit,
    onDeleteBoard: (BoardSummary) -> Unit,
    snackbarHostState: SnackbarHostState
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
            BoardsTopBar(
                title = headerTitle,
                searchQuery = state.searchQuery,
                onOpenDrawer = onOpenDrawer,
                onSearchChange = onSearchChange
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreateBoard(defaultBoardPrefix) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_board)) }
            )
        }
    ) { innerPadding ->
        if (state.boards.isEmpty()) {
            BoardsEmptyState(
                searchQuery = state.searchQuery,
                filter = state.selectedFilter,
                contentPadding = innerPadding
            )
        } else {
            BoardsGrid(
                boards = state.boards,
                folderNamesById = folderNamesById,
                showFolderLabel = state.selectedFilter is FolderFilter.All,
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 88.dp
                ),
                onOpenBoard = onOpenBoard,
                onRequestRename = onRequestRenameBoard,
                onRequestMove = onRequestMoveBoard,
                onDelete = onDeleteBoard
            )
        }
    }
}