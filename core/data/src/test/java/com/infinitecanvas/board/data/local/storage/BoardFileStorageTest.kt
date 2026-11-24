package com.infinitecanvas.board.data.local.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.infinitecanvas.board.data.testing.testImage
import com.infinitecanvas.board.data.testing.testStroke
import com.infinitecanvas.board.data.testing.testViewport
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.ViewportState
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
    fun `round-trip current version preserves all fields`() = runTest {
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
        assertThat(payload.strokes[1].brush.opacity).isEqualTo(0.42f)
        assertThat(payload.images).hasSize(2)
        assertThat(payload.images[1].isPinned).isTrue()
        assertThat(payload.images[1].zIndex).isEqualTo(3)
    }

    @Test
    fun `read v1 file without images and without timestamps`() = runTest {
        writeV1(emptyList(), viewport = testViewport())
        val payload = storage.readBoardPayload(boardId)

        assertThat(payload.images).isEmpty()
        assertThat(payload.strokes).isEmpty()
        assertThat(payload.viewport.scale).isEqualTo(2.0)
    }

    @Test
    fun `read v1 stroke gets zero timestamp`() = runTest {
        writeV1(strokes = listOf(1L to 3), viewport = testViewport())
        val payload = storage.readBoardPayload(boardId)

        assertThat(payload.strokes).hasSize(1)
        assertThat(payload.strokes[0].points).hasSize(3)
        assertThat(payload.strokes[0].points.all { it.timestampMs == 0L }).isTrue()
    }

    @Test
    fun `read v2 file has images without isPinned`() = runTest {
        writeV2(strokes = emptyList(), images = listOf("img.webp"), viewport = testViewport())
        val payload = storage.readBoardPayload(boardId)

        assertThat(payload.images).hasSize(1)
        assertThat(payload.images[0].fileName).isEqualTo("img.webp")
        assertThat(payload.images[0].isPinned).isFalse()
    }

    @Test
    fun `read v3 file honors isPinned`() = runTest {
        writeV3(strokes = emptyList(), images = listOf("pinned.webp" to true), viewport = testViewport())
        val payload = storage.readBoardPayload(boardId)

        assertThat(payload.images).hasSize(1)
        assertThat(payload.images[0].isPinned).isTrue()
    }

    @Test
    fun `v3 pencil ordinal migrates to translucent ballpoint`() = runTest {
        // ordinal 2 в v1..v3 = удалённый PENCIL; должен читаться как BALLPOINT
        // с opacity * 0.45, зажатой в 0.1..1.
        writeV3(strokes = listOf(1L to 2), images = emptyList(), viewport = testViewport())
        val payload = storage.readBoardPayload(boardId)

        val brush = payload.strokes.single().brush
        assertThat(brush.type).isEqualTo(BrushType.BALLPOINT)
        assertThat(brush.opacity).isWithin(0.01f).of(0.45f)
    }

    @Test
    fun `bad magic returns empty payload`() = runTest {
        val file = storage.getBoardFile(boardId).apply { parentFile?.mkdirs() }
        DataOutputStream(FileOutputStream(file)).use {
            it.writeInt(0xDEADBEEF.toInt()) // не "ICB1"
            it.writeInt(4)
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
            it.writeInt(4)
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
            it.writeInt(4)
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

    // ── Ручные писатели старых версий ────────────────────────────────

    private fun writeV1(
        strokes: List<Pair<Long, Int>> = emptyList(),
        viewport: ViewportState
    ) = writeLegacy(version = 1, strokes = strokes, images = emptyList(), viewport = viewport)

    private fun writeV2(
        strokes: List<Long> = emptyList(),
        images: List<String> = emptyList(),
        viewport: ViewportState
    ) = writeLegacy(
        version = 2,
        strokes = strokes.map { it to BrushType.BALLPOINT.ordinal },
        images = images.map { it to false },
        viewport = viewport
    )

    private fun writeV3(
        strokes: List<Pair<Long, Int>> = emptyList(),
        images: List<Pair<String, Boolean>> = emptyList(),
        viewport: ViewportState
    ) = writeLegacy(version = 3, strokes = strokes, images = images, viewport = viewport)

    private fun writeLegacy(
        version: Int,
        strokes: List<Pair<Long, Int>>,       // id для пустого штриха; id to ordinal — если нужен кастом
        images: List<Pair<String, Boolean>>,
        viewport: ViewportState,
        pointsPerStroke: Int = 3
    ) {
        val file = storage.getBoardFile(boardId).apply { parentFile?.mkdirs() }
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeInt(0x49434231)
            out.writeInt(version)
            out.writeDouble(viewport.offsetX)
            out.writeDouble(viewport.offsetY)
            out.writeDouble(viewport.scale)

            out.writeInt(strokes.size)
            for ((id, ordinal) in strokes) {
                out.writeLong(id)
                out.writeInt(ordinal)
                out.writeInt(0xFF000000.toInt())
                out.writeFloat(4f)
                out.writeFloat(1f)
                out.writeDouble(0.0); out.writeDouble(0.0)
                out.writeDouble(10.0); out.writeDouble(10.0)
                out.writeInt(pointsPerStroke)
                repeat(pointsPerStroke) {
                    out.writeDouble(1.0); out.writeDouble(2.0); out.writeFloat(0.5f)
                    // v1..v3: timestampMs отсутствует
                }
            }

            if (version >= 2) {
                out.writeInt(images.size)
                for ((name, pinned) in images) {
                    out.writeLong(name.hashCode().toLong())
                    out.writeUTF(name)
                    out.writeDouble(0.0); out.writeDouble(0.0)
                    out.writeDouble(100.0); out.writeDouble(100.0)
                    out.writeInt(0)
                    if (version >= 3) out.writeBoolean(pinned)
                }
            }
        }
    }
}