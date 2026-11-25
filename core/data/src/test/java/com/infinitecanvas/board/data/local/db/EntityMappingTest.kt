package com.infinitecanvas.board.data.local.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntityMappingTest {

    @Test
    fun `BoardEntity toDomain maps all fields`() {
        val entity = BoardEntity(
            id = 7L,
            folderId = 3L,
            title = "Доска",
            updatedAt = 123L,
            thumbnailPath = "/t.webp",
            strokesCount = 42
        )

        val summary = entity.toDomain()

        assertThat(summary.id).isEqualTo(7L)
        assertThat(summary.folderId).isEqualTo(3L)
        assertThat(summary.title).isEqualTo("Доска")
        assertThat(summary.updatedAt).isEqualTo(123L)
        assertThat(summary.thumbnailPath).isEqualTo("/t.webp")
        assertThat(summary.strokesCount).isEqualTo(42)
    }

    @Test
    fun `BoardEntity toDomain preserves null folder and thumbnail`() {
        val entity = BoardEntity(id = 1L, title = "Empty")

        val summary = entity.toDomain()

        assertThat(summary.folderId).isNull()
        assertThat(summary.thumbnailPath).isNull()
    }

    @Test
    fun `FolderEntity toDomain maps all fields`() {
        val entity = FolderEntity(
            id = 9L,
            name = "Work",
            colorArgb = 0xFF112233.toInt(),
            sortOrder = 4,
            createdAt = 555L
        )

        val folder = entity.toDomain()

        assertThat(folder.id).isEqualTo(9L)
        assertThat(folder.name).isEqualTo("Work")
        assertThat(folder.colorArgb).isEqualTo(0xFF112233.toInt())
        assertThat(folder.sortOrder).isEqualTo(4)
        assertThat(folder.createdAt).isEqualTo(555L)
    }

    @Test
    fun `FolderEntity toDomain preserves null color`() {
        val folder = FolderEntity(id = 1L, name = "F").toDomain()
        assertThat(folder.colorArgb).isNull()
    }
}
