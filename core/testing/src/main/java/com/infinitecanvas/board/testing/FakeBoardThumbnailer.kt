package com.infinitecanvas.board.testing

import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.port.BoardThumbnailer

/**
 * Фейк порта превью: фиксирует вызовы и позволяет управлять ответом.
 *
 * Дефолт result = "/fake/thumb.webp" — как у реального ThumbnailGenerator при
 * непустом контенте. Тест «пустая доска» выставляет result = null, чтобы
 * проверить, что BoardViewModel не подменяет null на прежний путь в обход
 * BoardRepositoryImpl (там null уходит в COALESCE).
 */
class FakeBoardThumbnailer : BoardThumbnailer {

    data class Call(
        val boardId: Long,
        val strokes: List<Stroke>,
        val images: List<BoardImage>
    )

    val calls = mutableListOf<Call>()
    var result: String? = "/fake/thumb.webp"
    var throwOnCall: Throwable? = null

    override suspend fun generateAndSave(
        boardId: Long,
        strokes: List<Stroke>,
        images: List<BoardImage>
    ): String? {
        throwOnCall?.let { throw it }
        calls += Call(boardId, strokes, images)
        return result
    }
}