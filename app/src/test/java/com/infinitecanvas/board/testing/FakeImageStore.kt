package com.infinitecanvas.board.testing

import android.net.Uri
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.domain.port.ImportedImage
import java.io.File

/**
 * Фейк порта файлов изображений: возвращает виртуальные пути и пишет вызовы.
 *
 * fileFor не создаёт каталогов и не трогает диск — файлы по этим путям
 * не существуют. Для BoardViewModelTest это и нужно: BoardImageCache в
 * фейковом окружении не запускается, а BoardImageController не трогается,
 * кроме путей, которые явно просит тест.
 *
 * importCalls хранит Uri как есть — тест сам решит, важен ли ему конкретный Uri
 * (в фазе 3 не важен, важен сам факт вызова).
 */
class FakeImageStore : ImageStore {

    val fileForCalls = mutableListOf<Pair<Long, String>>()
    val importCalls = mutableListOf<Pair<Long, Uri>>()
    val deleteCalls = mutableListOf<Pair<Long, String>>()
    val deleteAllCalls = mutableListOf<Long>()

    var importedImage: ImportedImage? = null

    override fun fileFor(boardId: Long, fileName: String): File {
        fileForCalls += boardId to fileName
        return File("/fake/board_$boardId/$fileName")
    }

    override fun createTempCameraFile(): File =
        File("/fake/camera_${System.nanoTime()}.jpg")

    override suspend fun importFromUri(boardId: Long, uri: Uri): ImportedImage? {
        importCalls += boardId to uri
        return importedImage
    }

    override suspend fun delete(boardId: Long, fileName: String): Boolean {
        deleteCalls += boardId to fileName
        return true
    }

    override suspend fun deleteAll(boardId: Long) {
        deleteAllCalls += boardId
    }
}