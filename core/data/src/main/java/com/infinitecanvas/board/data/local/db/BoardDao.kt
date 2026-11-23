package com.infinitecanvas.board.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {
    // --- Папки ---
    // Сортировка: sort_order (поле для ручного порядка), затем created_at.
    // Имя не участвует — переименование не должно переставлять папки.
    @Query("SELECT * FROM folders ORDER BY sort_order ASC, created_at ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :newName WHERE id = :folderId")
    suspend fun renameFolder(folderId: Long, newName: String)

    // FK boards.folder_id ON DELETE SET NULL сработает благодаря Room (foreign_keys=ON):
    // доски не удалятся, а станут «без папки».
    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Long)

    // --- Доски ---
    // Свежие изменения сверху; тот же порядок должен сохраняться во всех трёх
    // выборках, иначе списки в разных фильтрах будут вести себя по-разному.
    @Query("SELECT * FROM boards ORDER BY updated_at DESC")
    fun observeAllBoards(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards WHERE folder_id = :folderId ORDER BY updated_at DESC")
    fun observeBoardsByFolder(folderId: Long): Flow<List<BoardEntity>>

    // IS NULL, а не = :folderId: сравнение с параметром не находит строки с NULL.
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
    // COALESCE(:thumbnailPath, thumbnail_path): null = «оставить прежнее превью».
    // Удалить превью этим запросом нельзя — только заменить на новый путь.
    suspend fun updateBoardStats(
        boardId: Long,
        updatedAt: Long,
        strokesCount: Int,
        thumbnailPath: String?
    )

    // updated_at меняется вручную (как и в moveBoardToFolder): @Query его не трогает,
    // а от него зависит порядок в observeAllBoards.
    @Query("UPDATE boards SET title = :newTitle, updated_at = :updatedAt WHERE id = :boardId")
    suspend fun renameBoard(boardId: Long, newTitle: String, updatedAt: Long)

    @Query("UPDATE boards SET folder_id = :targetFolderId, updated_at = :updatedAt WHERE id = :boardId")
    suspend fun moveBoardToFolder(boardId: Long, targetFolderId: Long?, updatedAt: Long)

    @Query("DELETE FROM boards WHERE id = :boardId")
    suspend fun deleteBoard(boardId: Long)

    @Query("SELECT title FROM boards")
    suspend fun getAllBoardTitles(): List<String>
}