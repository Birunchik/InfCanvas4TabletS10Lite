package com.infinitecanvas.board.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.infinitecanvas.board.data.local.db.AppDatabase
import com.infinitecanvas.board.data.local.db.BoardDao
import com.infinitecanvas.board.data.local.storage.BoardFileStorage
import com.infinitecanvas.board.data.testing.testImage
import com.infinitecanvas.board.data.testing.testStroke
import com.infinitecanvas.board.data.testing.testViewport
import com.infinitecanvas.board.domain.port.ImageStore
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class BoardRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: BoardDao
    private lateinit var fileStorage: BoardFileStorage
    private lateinit var imageStore: ImageStore
    private lateinit var repo: BoardRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.boardDao()
        fileStorage = BoardFileStorage(context)
        imageStore = mockk(relaxed = true)
        repo = BoardRepositoryImpl(dao = dao, fileStorage = fileStorage, imageStore = imageStore)
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `createBoard inserts row and writes empty payload`() = runTest {
        val id = repo.createBoard(title = "Board 1")

        assertThat(id).isGreaterThan(0L)
        val row = dao.getBoardById(id)
        assertThat(row).isNotNull()
        assertThat(row!!.title).isEqualTo("Board 1")

        val payload = fileStorage.readBoardPayload(id)
        assertThat(payload.strokes).isEmpty()
        assertThat(payload.images).isEmpty()
    }

    @Test
    fun `createBoard trims title`() = runTest {
        val id = repo.createBoard("   Spaces   ")
        assertThat(dao.getBoardById(id)!!.title).isEqualTo("Spaces")
    }

    @Test
    fun `saveBoardContent writes file and updates Room`() = runTest {
        val id = repo.createBoard("b")
        val strokes = listOf(testStroke(id = 1L), testStroke(id = 2L))
        val images = listOf(testImage(id = 10L))

        repo.saveBoardContent(id, strokes, images, testViewport(), thumbnailPath = "/t.webp")

        val payload = fileStorage.readBoardPayload(id)
        assertThat(payload.strokes).hasSize(2)
        assertThat(payload.images).hasSize(1)

        val row = dao.getBoardById(id)!!
        assertThat(row.strokesCount).isEqualTo(2)
        assertThat(row.thumbnailPath).isEqualTo("/t.webp")
    }

    @Test
    fun `saveBoardContent with null thumbnail does not erase existing`() = runTest {
        val id = repo.createBoard("b")
        // Первая запись — с превью
        repo.saveBoardContent(id, emptyList(), emptyList(), testViewport(), "/old.webp")

        // Вторая — thumbnailPath=null, COALESCE должен оставить /old.webp
        repo.saveBoardContent(id, listOf(testStroke()), emptyList(), testViewport(), thumbnailPath = null)

        val row = dao.getBoardById(id)!!
        assertThat(row.thumbnailPath).isEqualTo("/old.webp")
        assertThat(row.strokesCount).isEqualTo(1)
    }

    @Test
    fun `loadBoard returns null for missing board`() = runTest {
        assertThat(repo.loadBoard(boardId = 999_999L)).isNull()
    }

    @Test
    fun `loadBoard returns document with summary and payload`() = runTest {
        val id = repo.createBoard("doc")
        val strokes = listOf(testStroke(id = 1L))
        repo.saveBoardContent(id, strokes, emptyList(), testViewport(), "/p.webp")

        val doc = repo.loadBoard(id)
        assertThat(doc).isNotNull()
        assertThat(doc!!.summary.title).isEqualTo("doc")
        assertThat(doc.strokes).hasSize(1)
    }

    @Test
    fun `deleteBoard removes row file and assets`() = runTest {
        val id = repo.createBoard("gone")
        repo.saveBoardContent(id, listOf(testStroke()), emptyList(), testViewport())
        val bin = fileStorage.getBoardFile(id)
        assertThat(bin.exists()).isTrue()

        repo.deleteBoard(id)

        assertThat(dao.getBoardById(id)).isNull()
        assertThat(bin.exists()).isFalse()
        coVerify(exactly = 1) { imageStore.deleteAll(id) }
    }

    @Test
    fun `deleteFolder does not delete boards only nulls folderId`() = runTest {
        val folderId = repo.createFolder(name = "F1")
        val boardId = repo.createBoard("inFolder", folderId = folderId)

        repo.deleteFolder(folderId)

        val row = dao.getBoardById(boardId)
        assertThat(row).isNotNull()
        assertThat(row!!.folderId).isNull()
    }

    @Test
    fun `getSuggestedBoardTitle parses existing numeric suffixes`() = runTest {
        repo.createBoard("Доска 1")
        repo.createBoard("Доска 3")
        repo.createBoard("Доска 12 (копия)") // не должен участвовать

        val next = repo.getSuggestedBoardTitle("Доска")
        assertThat(next).isEqualTo("Доска 4")
    }

    @Test
    fun `getSuggestedBoardTitle starts at one for empty`() = runTest {
        assertThat(repo.getSuggestedBoardTitle("Доска")).isEqualTo("Доска 1")
    }
}