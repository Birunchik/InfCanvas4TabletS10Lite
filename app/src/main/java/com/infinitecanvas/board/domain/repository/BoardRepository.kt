package com.infinitecanvas.board.domain.repository

import com.infinitecanvas.board.domain.model.BoardDocument
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Folder
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import kotlinx.coroutines.flow.Flow

// Контракт не знает про Room и файлы: порядок записи «сначала файл, потом БД» —
// обязанность реализации, интерфейс её не фиксирует.
interface BoardRepository {
    fun observeFolders(): Flow<List<Folder>>
    fun observeAllBoards(): Flow<List<BoardSummary>>
    // «Без папки» — отдельный метод: сравнение `folder_id = :folderId` не
    // возвращает строки с NULL, поэтому нужен явный IS NULL.
    fun observeBoardsByFolder(folderId: Long): Flow<List<BoardSummary>>
    fun observeUnassignedBoards(): Flow<List<BoardSummary>>

    suspend fun createFolder(name: String, colorArgb: Int? = null): Long
    suspend fun renameFolder(folderId: Long, newName: String)
    // Доски папки не удаляются: FK onDelete=SET_NULL переводит их в «без папки».
    suspend fun deleteFolder(folderId: Long)

    suspend fun createBoard(title: String, folderId: Long? = null): Long
    suspend fun loadBoard(boardId: Long): BoardDocument?
    // thumbnailPath=null — «не трогать превью», а не «удалить»: реализация
    // прокидывает null в COALESCE(:thumbnailPath, thumbnail_path).
    suspend fun saveBoardContent(
        boardId: Long,
        strokes: List<Stroke>,
        images: List<BoardImage>,
        viewport: ViewportState,
        thumbnailPath: String? = null
    )
    suspend fun renameBoard(boardId: Long, newTitle: String)
    suspend fun moveBoardToFolder(boardId: Long, targetFolderId: Long?)
    suspend fun deleteBoard(boardId: Long)
}