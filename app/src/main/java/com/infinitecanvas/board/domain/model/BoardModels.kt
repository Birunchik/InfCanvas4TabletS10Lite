package com.infinitecanvas.board.domain.model

data class BoardSummary(
    val id: Long,
    val folderId: Long?,
    val title: String,
    val updatedAt: Long,
    val thumbnailPath: String?,
    val strokesCount: Int
)
data class BoardDocument(
    val summary: BoardSummary,
    val strokes: List<Stroke>,
    val images: List<BoardImage> = emptyList(),
    val viewport: ViewportState
)