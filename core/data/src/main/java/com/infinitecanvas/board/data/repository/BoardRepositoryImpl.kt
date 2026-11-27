package com.infinitecanvas.board.data.repository

import com.infinitecanvas.board.data.local.db.BoardDao
import com.infinitecanvas.board.data.local.db.BoardEntity
import com.infinitecanvas.board.data.local.db.FolderEntity
import com.infinitecanvas.board.data.local.storage.BoardFileStorage
import com.infinitecanvas.board.domain.model.BoardDocument
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Folder
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.sortedForDisplay
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.domain.repository.BoardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Два хранилища без общей транзакции: геометрия в файле, метаданные в Room.
// Порядок вызовов и есть политика согласованности — менять его нельзя.
// internal: наружу отдаётся только доменный BoardRepository (см. createBoardRepository).
internal class BoardRepositoryImpl(
    private val dao: BoardDao,
    private val fileStorage: BoardFileStorage,
    private val imageStore: ImageStore
) : BoardRepository {

    // Flow из Room эмитит на любое изменение таблицы; маппинг в домен дешёвый,
    // но выполняется на каждом эмите — не добавлять сюда чтение файлов.
    override fun observeFolders(): Flow<List<Folder>> =
        dao.observeFolders().map { list ->
            list.map { it.toDomain() }.sortedForDisplay()
        }

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

    // Доски не трогаем: FK SET_NULL в Room переведёт их в «без папки».
    override suspend fun deleteFolder(folderId: Long) {
        dao.deleteFolder(folderId)
    }

    // Сначала строка Room (нужен autoGenerate id), затем пустой payload. Если упасть
    // между ними, доска откроется пустой — loadBoard терпит отсутствие файла.
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
    override suspend fun getSuggestedBoardTitle(prefix: String): String {
        val titles = dao.getAllBoardTitles()
        // Точное совпадение "$prefix N": "Доска 12" парсится, "Доска 12 (копия)" — нет
        val pattern = Regex("^${Regex.escape(prefix)} (\\d+)$")
        val maxNumber = titles
            .mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "$prefix ${maxNumber + 1}"
    }

    // Сначала метаданные: нет строки — доска удалена, файл можно не читать.
    // Файл и summary читаются раздельно, поэтому теоретически могут разойтись.
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

    // Порядок «сначала файл, потом Room» обязателен: при падении записи updated_at
    // не соврёт, а обратный порядок оставил бы БД со ссылкой на старую геометрию.
    override suspend fun saveBoardContent(
        boardId: Long,
        strokes: List<Stroke>,
        images: List<BoardImage>,
        viewport: ViewportState,
        thumbnailPath: String?
    ) {
        // thumbnailPath=null уходит в COALESCE: превью не перегенерировалось —
        // оставляем прежний путь, а не затираем его.
        fileStorage.writeBoardPayload(boardId, strokes, images, viewport)
        dao.updateBoardStats(
            boardId = boardId,
            updatedAt = System.currentTimeMillis(),
            strokesCount = strokes.size,
            thumbnailPath = thumbnailPath
        )
    }

    // updatedAt передаётся явно: от него зависит порядок в списках, а @Query
    // не обновляет поле автоматически.
    override suspend fun renameBoard(boardId: Long, newTitle: String) {
        dao.renameBoard(boardId, newTitle.trim(), System.currentTimeMillis())
    }

    // null допустим: «переместить в корень» = folder_id IS NULL.
    override suspend fun moveBoardToFolder(boardId: Long, targetFolderId: Long?) {
        dao.moveBoardToFolder(boardId, targetFolderId, System.currentTimeMillis())
    }

    // Сначала строка в Room (источник истины), затем файлы: осиротевшие файлы
    // безопаснее строки, которая ссылается на уже удалённые данные.
    override suspend fun deleteBoard(boardId: Long) {
        dao.deleteBoard(boardId)
        fileStorage.deleteBoardFiles(boardId)
        imageStore.deleteAll(boardId)
    }
}