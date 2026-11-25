package com.infinitecanvas.board.data.local.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.infinitecanvas.board.data.testing.testImage
import com.infinitecanvas.board.data.testing.testStroke
import com.infinitecanvas.board.data.testing.testViewport
import java.io.DataOutputStream
import java.io.EOFException
import java.io.FileOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class BoardFileStorageTest {

    private lateinit var context: Context
    private lateinit var storage: BoardFileStorage
    private val boardId = 42L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = BoardFileStorage(context)
    }

    @Test
    fun `round-trip preserves all fields`() = runTest {
        val strokes = listOf(
            testStroke(id = 7L),
            testStroke(id = 8L, brush = testStroke().brush.copy(opacity = 0.42f))
        )
        val images = listOf(
            testImage(id = 100L, fileName = "a.webp"),
            testImage(id = 101L, fileName = "b.webp", isPinned = true, zIndex = 3)
        )
        val viewport = testViewport(offsetX = 12.5, offsetY = -3.5, scale = 1.75)

        storage.writeBoardPayload(boardId, strokes, images, viewport)
        val payload = storage.readBoardPayload(boardId)

        assertThat(payload.viewport).isEqualTo(viewport)
        assertThat(payload.strokes).hasSize(2)
        assertThat(payload.strokes[0].id).isEqualTo(7L)
        assertThat(payload.strokes[0].points[1].timestampMs).isEqualTo(16L)
        assertThat(payload.strokes[1].brush.opacity).isEqualTo(0.42f)
        assertThat(payload.images).hasSize(2)
        assertThat(payload.images[1].isPinned).isTrue()
        assertThat(payload.images[1].zIndex).isEqualTo(3)
    }

    @Test
    fun `unsupported version returns empty payload`() = runTest {
        // Поддерживается только текущая раскладка; любой другой номер версии
        // (в том числе прежняя v4) читается как пустая доска.
        writeHeader(version = 2)
        val payload = storage.readBoardPayload(boardId)

        assertThat(payload.strokes).isEmpty()
        assertThat(payload.images).isEmpty()
    }

    @Test
    fun `previously written v4 file is now rejected`() = runTest {
        writeHeader(version = 4)
        val payload = storage.readBoardPayload(boardId)

        assertThat(payload.strokes).isEmpty()
        assertThat(payload.images).isEmpty()
    }

    @Test
    fun `bad magic returns empty payload`() = runTest {
        val file = storage.getBoardFile(boardId).apply { parentFile?.mkdirs() }
        DataOutputStream(FileOutputStream(file)).use {
            it.writeInt(0xDEADBEEF.toInt()) // не "ICB1"
            it.writeInt(1)
            it.writeDouble(0.0)
        }

        val payload = storage.readBoardPayload(boardId)
        assertThat(payload.strokes).isEmpty()
        assertThat(payload.images).isEmpty()
    }

    @Test
    fun `truncated file throws EOFException`() = runTest {
        val file = storage.getBoardFile(boardId).apply { parentFile?.mkdirs() }
        DataOutputStream(FileOutputStream(file)).use {
            it.writeInt(0x49434231) // "ICB1"
            it.writeInt(1)
            it.writeDouble(1.0)
            it.writeDouble(2.0)
            // scale: double — обрывается здесь
        }

        assertThrows(EOFException::class.java) {
            kotlinx.coroutines.runBlocking { storage.readBoardPayload(boardId) }
        }
    }

    @Test
    fun `garbage strokeCount does not OOM and surfaces EOF`() = runTest {
        val file = storage.getBoardFile(boardId).apply { parentFile?.mkdirs() }
        DataOutputStream(FileOutputStream(file)).use {
            it.writeInt(0x49434231)
            it.writeInt(1)
            it.writeDouble(0.0); it.writeDouble(0.0); it.writeDouble(1.0)
            it.writeInt(Int.MAX_VALUE) // мусор
            // тело штрихов отсутствует — после coerce должен упереться в EOF, не в OOM
        }

        // Без coerceIn ArrayList(Int.MAX_VALUE) бросит OOM/IllegalCapacity до EOF.
        assertThrows(EOFException::class.java) {
            kotlinx.coroutines.runBlocking { storage.readBoardPayload(boardId) }
        }
    }

    @Test
    fun `missing file returns empty payload`() = runTest {
        val payload = storage.readBoardPayload(boardId = 999_999L)
        assertThat(payload.strokes).isEmpty()
        assertThat(payload.images).isEmpty()
    }

    @Test
    fun `empty file returns empty payload`() = runTest {
        storage.getBoardFile(boardId).apply { parentFile?.mkdirs(); writeBytes(ByteArray(0)) }
        val payload = storage.readBoardPayload(boardId)
        assertThat(payload.strokes).isEmpty()
    }

    @Test
    fun `deleteBoardFiles removes bin and thumbnail`() = runTest {
        storage.writeBoardPayload(boardId, emptyList(), emptyList(), testViewport())
        val thumb = storage.getThumbnailFile(boardId).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
        val bin = storage.getBoardFile(boardId)
        assertThat(bin.exists()).isTrue()

        storage.deleteBoardFiles(boardId)

        assertThat(bin.exists()).isFalse()
        assertThat(thumb.exists()).isFalse()
    }

    // ── Хелпер: пишет валидный заголовок с заданным номером версии ─────────

    private fun writeHeader(version: Int) {
        val file = storage.getBoardFile(boardId).apply { parentFile?.mkdirs() }
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeInt(0x49434231) // "ICB1"
            out.writeInt(version)
            out.writeDouble(0.0); out.writeDouble(0.0); out.writeDouble(1.0) // viewport
            out.writeInt(0) // strokes
            out.writeInt(0) // images
        }
    }
}
