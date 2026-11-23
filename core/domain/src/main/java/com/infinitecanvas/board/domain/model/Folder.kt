package com.infinitecanvas.board.domain.model
// createdAt задаётся при создании и дальше не пересчитывается: от него зависит
// порядок папок при равном sortOrder.
data class Folder(
    val id: Long,
    val name: String,
    val colorArgb: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
public fun List<Folder>.sortedForDisplay(): List<Folder> =
    sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))