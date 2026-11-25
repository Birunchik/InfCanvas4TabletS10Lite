package com.infinitecanvas.board.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class BoardDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BoardDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.boardDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeFolders orders by sortOrder then createdAt`() = runTest {
        dao.insertFolder(FolderEntity(name = "C", sortOrder = 1, createdAt = 300))
        dao.insertFolder(FolderEntity(name = "A", sortOrder = 0, createdAt = 100))
        dao.insertFolder(FolderEntity(name = "B", sortOrder = 1, createdAt = 200))

        val names = dao.observeFolders().first().map { it.name }

        assertThat(names).containsExactly("A", "B", "C").inOrder()
    }

    @Test
    fun `observeAllBoards orders by updatedAt descending`() = runTest {
        dao.insertBoard(BoardEntity(title = "old", updatedAt = 100))
        dao.insertBoard(BoardEntity(title = "new", updatedAt = 300))
        dao.insertBoard(BoardEntity(title = "mid", updatedAt = 200))

        val titles = dao.observeAllBoards().first().map { it.title }

        assertThat(titles).containsExactly("new", "mid", "old").inOrder()
    }

    @Test
    fun `observeBoardsByFolder returns only boards of that folder`() = runTest {
        val folder = dao.insertFolder(FolderEntity(name = "F"))
        dao.insertBoard(BoardEntity(title = "in", folderId = folder))
        dao.insertBoard(BoardEntity(title = "out", folderId = null))

        val titles = dao.observeBoardsByFolder(folder).first().map { it.title }

        assertThat(titles).containsExactly("in")
    }

    @Test
    fun `observeUnassignedBoards returns only boards with null folder`() = runTest {
        val folder = dao.insertFolder(FolderEntity(name = "F"))
        dao.insertBoard(BoardEntity(title = "assigned", folderId = folder))
        dao.insertBoard(BoardEntity(title = "free", folderId = null))

        val titles = dao.observeUnassignedBoards().first().map { it.title }

        assertThat(titles).containsExactly("free")
    }

    @Test
    fun `insertBoard auto-generates increasing ids`() = runTest {
        val id1 = dao.insertBoard(BoardEntity(title = "a"))
        val id2 = dao.insertBoard(BoardEntity(title = "b"))

        assertThat(id1).isGreaterThan(0L)
        assertThat(id2).isGreaterThan(id1)
    }

    @Test
    fun `getBoardById returns entity or null`() = runTest {
        val id = dao.insertBoard(BoardEntity(title = "x"))

        assertThat(dao.getBoardById(id)?.title).isEqualTo("x")
        assertThat(dao.getBoardById(999_999L)).isNull()
    }

    @Test
    fun `updateBoardStats updates count and timestamp and coalesces thumbnail`() = runTest {
        val id = dao.insertBoard(BoardEntity(title = "b", thumbnailPath = "/old.webp"))

        dao.updateBoardStats(id, updatedAt = 200, strokesCount = 3, thumbnailPath = null)
        var row = dao.getBoardById(id)!!
        assertThat(row.strokesCount).isEqualTo(3)
        assertThat(row.updatedAt).isEqualTo(200)
        assertThat(row.thumbnailPath).isEqualTo("/old.webp") // COALESCE оставил старое

        dao.updateBoardStats(id, updatedAt = 300, strokesCount = 5, thumbnailPath = "/new.webp")
        row = dao.getBoardById(id)!!
        assertThat(row.thumbnailPath).isEqualTo("/new.webp")
        assertThat(row.strokesCount).isEqualTo(5)
    }

    @Test
    fun `renameBoard updates title and timestamp`() = runTest {
        val id = dao.insertBoard(BoardEntity(title = "old", updatedAt = 1))

        dao.renameBoard(id, "new", updatedAt = 999)

        val row = dao.getBoardById(id)!!
        assertThat(row.title).isEqualTo("new")
        assertThat(row.updatedAt).isEqualTo(999)
    }

    @Test
    fun `moveBoardToFolder moves and clears folder`() = runTest {
        val folder = dao.insertFolder(FolderEntity(name = "F"))
        val id = dao.insertBoard(BoardEntity(title = "b", folderId = null))

        dao.moveBoardToFolder(id, folder, updatedAt = 10)
        assertThat(dao.getBoardById(id)!!.folderId).isEqualTo(folder)

        dao.moveBoardToFolder(id, null, updatedAt = 11)
        assertThat(dao.getBoardById(id)!!.folderId).isNull()
    }

    @Test
    fun `deleteBoard removes only that row`() = runTest {
        val a = dao.insertBoard(BoardEntity(title = "a"))
        val b = dao.insertBoard(BoardEntity(title = "b"))

        dao.deleteBoard(a)

        assertThat(dao.getBoardById(a)).isNull()
        assertThat(dao.getBoardById(b)).isNotNull()
    }

    @Test
    fun `deleteFolder nulls boards folderId via FK SET_NULL`() = runTest {
        val folder = dao.insertFolder(FolderEntity(name = "F"))
        val id = dao.insertBoard(BoardEntity(title = "b", folderId = folder))

        dao.deleteFolder(folder)

        val row = dao.getBoardById(id)
        assertThat(row).isNotNull()
        assertThat(row!!.folderId).isNull()
    }

    @Test
    fun `getAllBoardTitles returns all titles`() = runTest {
        dao.insertBoard(BoardEntity(title = "Доска 1"))
        dao.insertBoard(BoardEntity(title = "Доска 3"))

        assertThat(dao.getAllBoardTitles()).containsExactly("Доска 1", "Доска 3")
    }

    @Test
    fun `insertFolder stores name and color and renameFolder updates name`() = runTest {
        val id = dao.insertFolder(FolderEntity(name = "F", colorArgb = 0xFF00FF00.toInt()))

        val folder = dao.observeFolders().first().first { it.id == id }
        assertThat(folder.colorArgb).isEqualTo(0xFF00FF00.toInt())

        dao.renameFolder(id, "Renamed")
        assertThat(dao.observeFolders().first().first().name).isEqualTo("Renamed")
    }
}
