package com.infinitecanvas.board.domain.port

import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.Stroke

interface BoardThumbnailer {
    suspend fun generateAndSave(
        boardId: Long,
        strokes: List<Stroke>,
        images: List<BoardImage>
    ): String?
}