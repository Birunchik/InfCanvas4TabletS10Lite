package com.infinitecanvas.board.domain.model

// Лёгкая проекция для списков: геометрии здесь нет, она читается только в
// loadBoard — список досок не должен тянуть бинарные файлы.
data class BoardSummary(
    val id: Long,
    val folderId: Long?,
    val title: String,
    val updatedAt: Long,
    val thumbnailPath: String?,
    val strokesCount: Int
)
// summary.thumbnailPath может отставать от содержимого: превью перегенерируется
// не при каждом сохранении, а null в saveBoardContent означает «оставить старое».
data class BoardDocument(
    val summary: BoardSummary,
    val strokes: List<Stroke>,
    val images: List<BoardImage> = emptyList(),
    val viewport: ViewportState
)