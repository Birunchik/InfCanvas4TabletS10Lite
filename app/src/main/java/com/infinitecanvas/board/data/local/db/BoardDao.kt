package com.infinitecanvas.board.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {
    // --- Папки ---
    @Query("SELECT * FROM folders ORDER BY sort_order ASC, created_at ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :newName WHERE id = :folderId")
    suspend fun renameFolder(folderId: Long, newName: String)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Long)

    // --- Доски ---
    @Query("SELECT * FROM boards ORDER BY updated_at DESC")
    fun observeAllBoards(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards WHERE folder_id = :folderId ORDER BY updated_at DESC")
    fun observeBoardsByFolder(folderId: Long): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards WHERE folder_id IS NULL ORDER BY updated_at DESC")
    fun observeUnassignedBoards(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards WHERE id = :boardId LIMIT 1")
    suspend fun getBoardById(boardId: Long): BoardEntity?

    @Insert
    suspend fun insertBoard(board: BoardEntity): Long

    @Query(
        """
        UPDATE boards 
        SET updated_at = :updatedAt, 
            strokes_count = :strokesCount,
            thumbnail_path = COALESCE(:thumbnailPath, thumbnail_path)
        WHERE id = :boardId
        """
    )
    suspend fun updateBoardStats(
        boardId: Long,
        updatedAt: Long,
        strokesCount: Int,
        thumbnailPath: String?
    )

    @Query("UPDATE boards SET title = :newTitle, updated_at = :updatedAt WHERE id = :boardId")
    suspend fun renameBoard(boardId: Long, newTitle: String, updatedAt: Long)

    @Query("UPDATE boards SET folder_id = :targetFolderId, updated_at = :updatedAt WHERE id = :boardId")
    suspend fun moveBoardToFolder(boardId: Long, targetFolderId: Long?, updatedAt: Long)

    @Query("DELETE FROM boards WHERE id = :boardId")
    suspend fun deleteBoard(boardId: Long)
}