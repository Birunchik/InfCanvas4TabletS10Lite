package com.infinitecanvas.board.presentation.screen.home.pane

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.presentation.screen.home.card.BoardPreviewCard

/**
 * Грид карточек досок. Адаптивные колонки (minSize = 250dp): на телефоне —
 * одна колонка, на планшете — 2-4 в зависимости от ширины.
 */
@Composable
internal fun BoardsGrid(
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
            // key = id обязателен: иначе при удалении или фильтрации Compose
            // переиспользует состояние чужих карточек.
            key = { it.id },
            contentType = { "board_card" }
        ) { board ->
            // Метку папки показываем только в «Все доски»: внутри папки
            // она дублировала бы заголовок.
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