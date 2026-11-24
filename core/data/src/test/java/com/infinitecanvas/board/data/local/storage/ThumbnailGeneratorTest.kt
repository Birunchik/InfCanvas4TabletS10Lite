package com.infinitecanvas.board.data.local.storage

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.infinitecanvas.board.data.testing.testImage
import com.infinitecanvas.board.data.testing.testStroke
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.domain.port.ImageStore
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailGeneratorTest {

    private lateinit var context: Context
    private lateinit var fileStorage: BoardFileStorage
    private lateinit var imageStore: ImageStore
    private lateinit var generator: ThumbnailGenerator
    private val boardId = 7L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fileStorage = BoardFileStorage(context)
        imageStore = mockk(relaxed = true)
        generator = ThumbnailGenerator(fileStorage = fileStorage, imageStore = imageStore)
    }

    @Test
    fun `empty board returns null`() = runTest {
        val result = generator.generateAndSave(boardId, emptyList(), emptyList())
        assertThat(result).isNull()
        assertThat(fileStorage.getThumbnailFile(boardId).exists()).isFalse()
    }

    @Test
    fun `empty board deletes pre-existing thumbnail`() = runTest {
        val thumb = fileStorage.getThumbnailFile(boardId).apply {
            parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3))
        }
        assertThat(thumb.exists()).isTrue()

        val result = generator.generateAndSave(boardId, emptyList(), emptyList())

        assertThat(result).isNull()
        assertThat(thumb.exists()).isFalse()
    }

    @Test
    fun `only pinned images returns null`() = runTest {
        val pinned = testImage(isPinned = true, bounds = WorldBounds(0.0, 0.0, 50.0, 50.0))
        val result = generator.generateAndSave(boardId, emptyList(), listOf(pinned))
        assertThat(result).isNull()
    }

    @Test
    fun `strokes produce a non-empty webp file`() = runTest {
        val strokes = listOf(testStroke(id = 1L), testStroke(id = 2L))

        val path = generator.generateAndSave(boardId, strokes, emptyList())

        assertThat(path).isNotNull()
        val file = File(path!!)
        assertThat(file.exists()).isTrue()
        assertThat(file.length()).isGreaterThan(0L)

        // RIFF....WEBP
        val header = file.readBytes().copyOfRange(0, 12)
        assertThat(String(header, 0, 4, Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(String(header, 8, 4, Charsets.US_ASCII)).isEqualTo("WEBP")
    }

    @Test
    fun `missing image file does not crash`() = runTest {
        val img = testImage(fileName = "missing.webp")
        every { imageStore.fileFor(boardId, "missing.webp") } returns
                File(context.cacheDir, "missing.webp")
        // В кэше файла нет — цикл отрисовки должен пропустить его и нарисовать только штрихи.

        val path = generator.generateAndSave(
            boardId,
            strokes = listOf(testStroke()),
            images = listOf(img)
        )
        assertThat(path).isNotNull()
        assertThat(File(path!!).exists()).isTrue()
    }

    @Test
    fun `thumbnail has THUMB_WIDTH x THUMB_HEIGHT dimensions`() = runTest {
        val path = generator.generateAndSave(
            boardId,
            strokes = listOf(testStroke()),
            images = emptyList()
        )
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        assertThat(opts.outWidth).isEqualTo(480)
        assertThat(opts.outHeight).isEqualTo(300)
    }
}