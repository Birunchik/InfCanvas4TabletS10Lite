package com.infinitecanvas.board.presentation.screen.home.pane

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.R
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.presentation.screen.home.FolderFilter
import com.infinitecanvas.board.presentation.screen.home.HomeUiState

/**
 * Правая (контентная) панель экрана досок: TopAppBar + поиск + FAB + грид.
 *
 * Про инсеты:
 *  - Scaffold получает WindowInsets.systemBars (без IME). Иначе innerPadding
 *    пересчитывается на каждом кадре анимации клавиатуры, и весь Scaffold
 *    пересобирается.
 *  - Контент сдвигается через Modifier.consumeWindowInsets(innerPadding)
 *    .imePadding(): consume «съедает» уже применённые системные бары, и
 *    imePadding добавляет только недостающее до IME. Без consume получается
 *    navBar + ime (список уходит в пустоту, FAB выше, чем нужно).
 *  - FAB не поднимаем над IME: Scaffold сам применяет systemBars к слоту FAB,
 *    а дополнительный imePadding дал бы navBar + ime. При открытой клавиатуре
 *    FAB и не нужен — пользователь вводит в поиск, а не создаёт доску.
 */
@Composable
internal fun BoardsGridPane(
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
        contentWindowInsets = WindowInsets.systemBars,
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
        // padding(innerPadding): отрезает системные бары (TopAppBar сверху,
        // navBar снизу) — контент не уходит под них.
        // consumeWindowInsets(innerPadding): говорит «эти инсеты уже обработаны»,
        // чтобы imePadding() не задваивал их.
        // imePadding(): добавляет только высоту IME — локально, без re-subcompose
        // всего Scaffold.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            if (state.boards.isEmpty()) {
                EmptyBoardsContent(
                    searchQuery = state.searchQuery,
                    filter = state.selectedFilter,
                )
            } else {
                BoardsGrid(
                    boards = state.boards,
                    folderNamesById = folderNamesById,
                    showFolderLabel = state.selectedFilter is FolderFilter.All,
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        // +88dp: FAB (56dp) + запас, чтобы нижний ряд не уходил
                        // под кнопку при скролле до конца.
                        bottom = 88.dp
                    ),
                    onOpenBoard = onOpenBoard,
                    onRequestRename = onRequestRenameBoard,
                    onRequestMove = onRequestMoveBoard,
                    onDelete = onDeleteBoard
                )
            }
        }
    }
}

@Composable
private fun EmptyBoardsContent(
    searchQuery: String,
    filter: FolderFilter
) {
    when {
        searchQuery.isNotBlank() -> EmptyState(
            titleRes = R.string.empty_search_title,
            subtitleRes = R.string.empty_search_subtitle
        )
        filter is FolderFilter.Custom -> EmptyState(
            titleRes = R.string.empty_folder_title,
            subtitleRes = R.string.empty_boards_subtitle
        )
        else -> EmptyState(
            titleRes = R.string.empty_boards_title,
            subtitleRes = R.string.empty_boards_subtitle
        )
    }
}