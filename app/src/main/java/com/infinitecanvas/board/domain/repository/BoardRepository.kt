package com.infinitecanvas.board.domain.repository

import com.infinitecanvas.board.domain.model.BoardDocument
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Folder
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import kotlinx.coroutines.flow.Flow

interface BoardRepository {
    fun observeFolders(): Flow<List<Folder>>
    fun observeAllBoards(): Flow<List<BoardSummary>>
    fun observeBoardsByFolder(folderId: Long): Flow<List<BoardSummary>>
    fun observeUnassignedBoards(): Flow<List<BoardSummary>>

    suspend fun createFolder(name: String, colorArgb: Int? = null): Long
    suspend fun renameFolder(folderId: Long, newName: String)
    suspend fun deleteFolder(folderId: Long)

    suspend fun createBoard(title: String, folderId: Long? = null): Long
    suspend fun loadBoard(boardId: Long): BoardDocument?
    suspend fun saveBoardContent(
        boardId: Long,
        strokes: List<Stroke>,
        viewport: ViewportState,
        thumbnailPath: String? = null
    )
    suspend fun renameBoard(boardId: Long, newTitle: String)
    suspend fun moveBoardToFolder(boardId: Long, targetFolderId: Long?)
    suspend fun deleteBoard(boardId: Long)
}