package com.infinitecanvas.board.domain.port

import android.net.Uri
import java.io.File

data class ImportedImage(
    val fileName: String,
    val widthPx: Int,
    val heightPx: Int
)

interface ImageStore {
    fun fileFor(boardId: Long, fileName: String): File
    fun createTempCameraFile(): File
    suspend fun importFromUri(boardId: Long, uri: Uri): ImportedImage?
    suspend fun delete(boardId: Long, fileName: String): Boolean
    suspend fun deleteAll(boardId: Long)
}