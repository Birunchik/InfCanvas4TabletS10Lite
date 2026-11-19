package com.infinitecanvas.board.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.infinitecanvas.board.domain.model.BoardSummary

// FK onDelete=SET_NULL: удаление папки не каскадится на доски. Индексы
// folder_id и updated_at обслуживают фильтр и сортировку списков.
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
    // NULL — валидное состояние «без папки», а не отсутствие данных.
    val folderId: Long? = null,
    val title: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "thumbnail_path")
    // Абсолютный путь к webp в filesDir/thumbnails; вне этого каталога не валиден.
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