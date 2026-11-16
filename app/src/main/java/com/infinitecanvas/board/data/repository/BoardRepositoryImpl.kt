package com.infinitecanvas.board.data.repository

import com.infinitecanvas.board.data.local.db.BoardDao
import com.infinitecanvas.board.data.local.db.BoardEntity
import com.infinitecanvas.board.data.local.db.FolderEntity
import com.infinitecanvas.board.data.local.storage.BoardFileStorage
import com.infinitecanvas.board.data.local.storage.ImageAssetStorage
import com.infinitecanvas.board.domain.model.BoardDocument
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Folder
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.repository.BoardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BoardRepositoryImpl(
    private val dao: BoardDao,
    private val fileStorage: BoardFileStorage,
    private val imageAssetStorage: ImageAssetStorage
) : BoardRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        dao.observeFolders().map { list -> list.map { it.toDomain() } }

    override fun observeAllBoards(): Flow<List<BoardSummary>> =
        dao.observeAllBoards().map { list -> list.map { it.toDomain() } }

    override fun observeBoardsByFolder(folderId: Long): Flow<List<BoardSummary>> =
        dao.observeBoardsByFolder(folderId).map { list -> list.map { it.toDomain() } }

    override fun observeUnassignedBoards(): Flow<List<BoardSummary>> =
        dao.observeUnassignedBoards().map { list -> list.map { it.toDomain() } }

    override suspend fun createFolder(name: String, colorArgb: Int?): Long {
        return dao.insertFolder(
            FolderEntity(
                name = name.trim(),
                colorArgb = colorArgb
            )
        )
    }

    override suspend fun renameFolder(folderId: Long, newName: String) {
        dao.renameFolder(folderId, newName.trim())
    }

    override suspend fun deleteFolder(folderId: Long) {
        dao.deleteFolder(folderId)
    }

    override suspend fun createBoard(title: String, folderId: Long?): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertBoard(
            BoardEntity(
                folderId = folderId,
                title = title.trim(),
                updatedAt = now
            )
        )
        fileStorage.writeBoardPayload(id, emptyList(), emptyList(), ViewportState())
        return id
    }

    override suspend fun loadBoard(boardId: Long): BoardDocument? {
        val entity = dao.getBoardById(boardId) ?: return null
        val payload = fileStorage.readBoardPayload(boardId)
        return BoardDocument(
            summary = entity.toDomain(),
            strokes = payload.strokes,
            images = payload.images,
            viewport = payload.viewport
        )
    }

    override suspend fun saveBoardContent(
        boardId: Long,
        strokes: List<Stroke>,
        images: List<BoardImage>,
        viewport: ViewportState,
        thumbnailPath: String?
    ) {
        fileStorage.writeBoardPayload(boardId, strokes, images, viewport)
        dao.updateBoardStats(
            boardId = boardId,
            updatedAt = System.currentTimeMillis(),
            strokesCount = strokes.size,
            thumbnailPath = thumbnailPath
        )
    }

    override suspend fun renameBoard(boardId: Long, newTitle: String) {
        dao.renameBoard(boardId, newTitle.trim(), System.currentTimeMillis())
    }

    override suspend fun moveBoardToFolder(boardId: Long, targetFolderId: Long?) {
        dao.moveBoardToFolder(boardId, targetFolderId, System.currentTimeMillis())
    }

    override suspend fun deleteBoard(boardId: Long) {
        dao.deleteBoard(boardId)
        fileStorage.deleteBoardFiles(boardId)
        imageAssetStorage.deleteAllBoardAssets(boardId)
    }
}