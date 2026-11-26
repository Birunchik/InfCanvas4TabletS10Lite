package com.infinitecanvas.board.presentation.screen.home

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitecanvas.feature.home.R
import com.infinitecanvas.board.presentation.screen.home.pane.BoardsGridPane
import com.infinitecanvas.board.presentation.screen.home.dialog.DeleteConfirmationDialog
import com.infinitecanvas.board.presentation.screen.home.sidebar.FolderSidebarContent
import com.infinitecanvas.board.presentation.screen.home.dialog.MoveToFolderDialog
import com.infinitecanvas.board.presentation.screen.home.dialog.TextInputDialog
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreen(
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
                message = context.getStringRes(error.messageResId)
            )
        }
    }

    // collect один раз на жизнь ViewModel: событие разовое, при рекомпозиции
    // повторной подписки не будет.
    LaunchedEffect(viewModel) {
        viewModel.openBoardEvents.collect { boardId ->
            onOpenBoard(boardId)
        }
    }

    // Диалоги — локальный remember, а не ViewModel: не переживают поворот
    // намеренно, зато нет «залипших» флагов.
    var dialogState by remember { mutableStateOf<HomeDialogState>(HomeDialogState.None) }

    val folderNamesById = remember(state.folders) {
        state.folders.associate { it.id to it.name }
    }

    // Ширина берётся из constraints самого контейнера, а не из
    // Configuration.screenWidthDp: в multi-window / freeform / split-screen
    // полная ширина экрана не совпадает с доступной. Поэтому @SuppressLint
    // на ConfigurationScreenWidthHeight больше не нужен ни здесь, ни наверху.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 600dp — брейкпоинт Material: ниже постоянная панель оставила бы
        // гриду меньше двух колонок.
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
                    snackbarHostState = snackbarHostState
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

/**
 * Обёртка над Context.getString для вызова из LaunchedEffect: messageResId
 * известен только в runtime (Flow-событие), поэтому stringResource не подходит —
 * это @Composable-функция, её нельзя вызывать из suspend-контекста.
 *
 * Линт LocalContextGetResourceValueCall ругается на context.getString в
 * @Composable-скоупе, потому что строка не обновится при смене локали. Здесь
 * это безопасно: LaunchedEffect(viewModel) перезапускается вместе с
 * Composable при смене конфигурации, а context в замыкании — всегда актуальный.
 * Суппресс локальный: правило по-прежнему ловит context.getString в композиции.
 */
@SuppressLint("LocalContextGetResourceValueCall")
private fun Context.getStringRes(@StringRes resId: Int): String = getString(resId)

