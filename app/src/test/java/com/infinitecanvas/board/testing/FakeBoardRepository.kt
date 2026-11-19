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
 * Управляемый фейк: тест сам решает, когда эмитить данные, и может
 * подменить любой suspend-метод, чтобы воспроизвести задержку или ошибку.
 */
class FakeBoardRepository : BoardRepository {

    val foldersFlow = MutableStateFlow<List<Folder>>(emptyList())
    val allBoardsFlow = MutableStateFlow<List<BoardSummary>>(emptyList())
    val boardsByFolderFlow = MutableStateFlow<Map<Long, List<BoardSummary>>>(emptyMap())
    val unassignedBoardsFlow = MutableStateFlow<List<BoardSummary>>(emptyList())

    var createFolderResult: (String) -> Long = { 1L }
    var createBoardResult: (String, Long?) -> Long = { _, _ -> 1L }
    var createBoardThrows: Throwable? = null
    var createFolderThrows: Throwable? = null

    val createdFolders = mutableListOf<String>()
    val createdBoards = mutableListOf<Pair<String, Long?>>()
    val renamedFolders = mutableListOf<Pair<Long, String>>()
    val renamedBoards = mutableListOf<Pair<Long, String>>()
    val movedBoards = mutableListOf<Pair<Long, Long?>>()
    val deletedFolders = mutableListOf<Long>()
    val deletedBoards = mutableListOf<Long>()

    var suggestedBoardTitleResult: (String) -> String = { prefix -> "$prefix 1" }

    override fun observeFolders(): Flow<List<Folder>> = foldersFlow.asStateFlow()

    override fun observeAllBoards(): Flow<List<BoardSummary>> = allBoardsFlow.asStateFlow()

    override fun observeBoardsByFolder(folderId: Long): Flow<List<BoardSummary>> =
        boardsByFolderFlow.map { it[folderId] ?: emptyList() }

    override fun observeUnassignedBoards(): Flow<List<BoardSummary>> = unassignedBoardsFlow.asStateFlow()

    override suspend fun createFolder(name: String, colorArgb: Int?): Long {
        createFolderThrows?.let { throw it }
        createdFolders += name
        return createFolderResult(name)
    }

    override suspend fun renameFolder(folderId: Long, newName: String) {
        renamedFolders += folderId to newName
    }

    override suspend fun deleteFolder(folderId: Long) {
        deletedFolders += folderId
    }

    override suspend fun createBoard(title: String, folderId: Long?): Long {
        createBoardThrows?.let { throw it }
        createdBoards += title to folderId
        return createBoardResult(title, folderId)
    }

    override suspend fun getSuggestedBoardTitle(prefix: String): String =
        suggestedBoardTitleResult(prefix)

    override suspend fun loadBoard(boardId: Long): BoardDocument? = null

    override suspend fun saveBoardContent(
        boardId: Long,
        strokes: List<Stroke>,
        images: List<BoardImage>,
        viewport: ViewportState,
        thumbnailPath: String?
    ) = Unit

    override suspend fun renameBoard(boardId: Long, newTitle: String) {
        renamedBoards += boardId to newTitle
    }

    override suspend fun moveBoardToFolder(boardId: Long, targetFolderId: Long?) {
        movedBoards += boardId to targetFolderId
    }

    override suspend fun deleteBoard(boardId: Long) {
        deletedBoards += boardId
    }
}