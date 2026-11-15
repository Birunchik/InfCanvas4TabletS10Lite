package com.infinitecanvas.board.domain.model

data class Folder(
    val id: Long,
    val name: String,
    val colorArgb: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)