package com.infinitecanvas.board.presentation.screen.home

import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Folder

/**
 * Выбранный фильтр в боковой панели папок.
 */
sealed interface FolderFilter {
    data object All : FolderFilter
    data object Unassigned : FolderFilter
    data class Custom(val folderId: Long, val folderName: String) : FolderFilter
}

data class HomeUiState(
    val folders: List<Folder> = emptyList(),
    val boards: List<BoardSummary> = emptyList(),
    val selectedFilter: FolderFilter = FolderFilter.All,
    val searchQuery: String = ""
)