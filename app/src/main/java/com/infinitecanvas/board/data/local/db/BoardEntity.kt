package com.infinitecanvas.board.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.infinitecanvas.board.domain.model.BoardSummary

@Entity(
    tableName = "boards",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["folder_id"]),
        Index(value = ["updated_at"])
    ]
)
data class BoardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "folder_id")
    val folderId: Long? = null,
    val title: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,
    @ColumnInfo(name = "strokes_count")
    val strokesCount: Int = 0
) {
    fun toDomain(): BoardSummary = BoardSummary(
        id = id,
        folderId = folderId,
        title = title,
        updatedAt = updatedAt,
        thumbnailPath = thumbnailPath,
        strokesCount = strokesCount
    )
}