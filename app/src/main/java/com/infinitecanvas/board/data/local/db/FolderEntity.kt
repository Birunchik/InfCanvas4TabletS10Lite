package com.infinitecanvas.board.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.infinitecanvas.board.domain.model.Folder

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Folder = Folder(
        id = id,
        name = name,
        colorArgb = colorArgb,
        sortOrder = sortOrder,
        createdAt = createdAt
    )
}