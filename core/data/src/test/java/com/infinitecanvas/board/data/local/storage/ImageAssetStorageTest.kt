package com.infinitecanvas.board.data.local.storage

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageAssetStorageTest {

    private val boardId = 1L
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var resolver: ContentResolver
    private lateinit var storage: ImageAssetStorage

    @Before
    fun setUp() {
        filesDir = createTempDir("files")
        cacheDir = createTempDir("cache")

        resolver = mockk(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns resolver

        storage = ImageAssetStorage(context)
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
        cacheDir.deleteRecursively()
    }

    @Test
    fun `fileFor returns path under board assets dir`() = runTest {
        val file = storage.fileFor(boardId, "img.webp")
        assertThat(file.name).isEqualTo("img.webp")
        assertThat(file.parentFile!!.name).isEqualTo("board_$boardId")
        assertThat(file.parentFile!!.parentFile!!.name).isEqualTo("board_assets")
    }

    @Test
    fun `createTempCameraFile returns jpg under cache camera dir`() = runTest {
        val file = storage.createTempCameraFile()
        assertThat(file.name).endsWith(".jpg")
        assertThat(file.parentFile!!.name).isEqualTo("camera_captures")
    }

    @Test
    fun `importFromUri downsamples large image to max 1600px`() = runTest {
        val png = writePng(width = 2000, height = 1000)
        every { resolver.openInputStream(any()) } returns FileInputStream(png)

        val imported = storage.importFromUri(boardId, Uri.parse("content://images/1"))

        assertThat(imported).isNotNull()
        assertThat(imported!!.widthPx).isEqualTo(1600)
        assertThat(imported.heightPx).isEqualTo(800)

        val out = storage.fileFor(boardId, imported.fileName)
        assertThat(out.exists()).isTrue()
        assertThat(isWebp(out)).isTrue()
    }

    @Test
    fun `importFromUri keeps small image unchanged`() = runTest {
        val png = writePng(width = 100, height = 50)
        every { resolver.openInputStream(any()) } returns FileInputStream(png)

        val imported = storage.importFromUri(boardId, Uri.parse("content://images/1"))

        assertThat(imported).isNotNull()
        assertThat(imported!!.widthPx).isEqualTo(100)
        assertThat(imported.heightPx).isEqualTo(50)
    }

    @Test
    fun `importFromUri returns null when stream cannot be opened`() = runTest {
        every { resolver.openInputStream(any()) } returns null

        val imported = storage.importFromUri(boardId, Uri.parse("content://images/1"))

        assertThat(imported).isNull()
    }

    @Test
    fun `importFromUri returns null for corrupt file`() = runTest {
        val corrupt = File(cacheDir, "corrupt.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        every { resolver.openInputStream(any()) } returns FileInputStream(corrupt)

        val imported = storage.importFromUri(boardId, Uri.parse("content://images/1"))

        assertThat(imported).isNull()
    }

    @Test
    fun `delete removes only the target file`() = runTest {
        val target = storage.fileFor(boardId, "a.webp").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
        val other = storage.fileFor(boardId, "b.webp").apply { writeBytes(byteArrayOf(2)) }

        val deleted = storage.delete(boardId, "a.webp")

        assertThat(deleted).isTrue()
        assertThat(target.exists()).isFalse()
        assertThat(other.exists()).isTrue()
    }

    @Test
    fun `deleteAll removes the whole board assets dir`() = runTest {
        val dir = storage.fileFor(boardId, "a.webp").parentFile!!.apply { mkdirs() }
        File(dir, "a.webp").writeBytes(byteArrayOf(1))
        File(dir, "b.webp").writeBytes(byteArrayOf(2))

        storage.deleteAll(boardId)

        assertThat(dir.exists()).isFalse()
    }

    // ── Хелперы ────────────────────────────────────────────────────────────

    private fun writePng(width: Int, height: Int): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val file = File(cacheDir, "src_${System.nanoTime()}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    private fun isWebp(file: File): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        // RIFF....WEBP — проверяем magic, который BitmapFactory успешно распознал
        val header = file.readBytes().copyOfRange(0, 12)
        return String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
    }

    private fun createTempDir(prefix: String): File =
        File(System.getProperty("java.io.tmpdir"), "infcanvas-$prefix-${System.nanoTime()}")
            .apply { mkdirs() }
}
