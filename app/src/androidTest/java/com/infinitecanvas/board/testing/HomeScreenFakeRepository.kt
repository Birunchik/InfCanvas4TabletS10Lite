package com.infinitecanvas.board.testing

import com.infinitecanvas.board.domain.model.BoardDocument
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Folder
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.repository.BoardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
/**
 * Минимальный фейк для HomeScreenTest: только чтение.
 *
 * Дублирует часть FakeBoardRepository из test/, потому что androidTest и test
 * не видят друг друга (AGP 9.0: sourceSets srcDir/directories не резолвятся).
 * Методы записи не реализованы — HomeScreenTest только читает состояние.
 */
class HomeScreenFakeRepository : BoardRepository {

    val foldersFlow = MutableStateFlow<List<Folder>>(emptyList())
    val allBoardsFlow = MutableStateFlow<List<BoardSummary>>(emptyList())
    val boardsByFolderFlow = MutableStateFlow<Map<Long, List<BoardSummary>>>(emptyMap())
    val unassignedBoardsFlow = MutableStateFlow<List<BoardSummary>>(emptyList())

    override fun observeFolders(): Flow<List<Folder>> = foldersFlow.asStateFlow()

    override fun observeAllBoards(): Flow<List<BoardSummary>> = allBoardsFlow.asStateFlow()

    override fun observeBoardsByFolder(folderId: Long): Flow<List<BoardSummary>> =
        boardsByFolderFlow.map { it[folderId] ?: emptyList() }

    override fun observeUnassignedBoards(): Flow<List<BoardSummary>> =
        unassignedBoardsFlow.asStateFlow()

    override suspend fun createFolder(name: String, colorArgb: Int?): Long =
        error("not used in HomeScreenTest")

    override suspend fun renameFolder(folderId: Long, newName: String) =
        error("not used in HomeScreenTest")

    override suspend fun deleteFolder(folderId: Long) =
        error("not used in HomeScreenTest")

    override suspend fun createBoard(title: String, folderId: Long?): Long =
        error("not used in HomeScreenTest")

    override suspend fun loadBoard(boardId: Long): BoardDocument? = null

    override suspend fun saveBoardContent(
        boardId: Long,
        strokes: List<Stroke>,
        images: List<BoardImage>,
        viewport: ViewportState,
        thumbnailPath: String?
    ) = Unit

    override suspend fun renameBoard(boardId: Long, newTitle: String) =
        error("not used in HomeScreenTest")

    override suspend fun moveBoardToFolder(boardId: Long, targetFolderId: Long?) =
        error("not used in HomeScreenTest")

    override suspend fun deleteBoard(boardId: Long) =
        error("not used in HomeScreenTest")

    override suspend fun getSuggestedBoardTitle(prefix: String): String {
        TODO("Not yet implemented")
    }
}