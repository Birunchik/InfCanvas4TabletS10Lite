package com.infinitecanvas.board.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderSortingTest {

    private fun folder(
        id: Long,
        sortOrder: Int = 0,
        createdAt: Long = 0L
    ) = Folder(id = id, name = "F$id", sortOrder = sortOrder, createdAt = createdAt)

    @Test
    fun `sorted by sortOrder, then createdAt`() {
        val folders = listOf(
            folder(id = 1, sortOrder = 2, createdAt = 100),
            folder(id = 2, sortOrder = 1, createdAt = 200),
            folder(id = 3, sortOrder = 1, createdAt = 100),
            folder(id = 4, sortOrder = 0, createdAt = 50),
        )

        val sorted = folders.sortedForDisplay()

        // sortOrder=0 → id=4 (createdAt=50)
        // sortOrder=1 → id=3 (createdAt=100), id=2 (createdAt=200)
        // sortOrder=2 → id=1
        assertEquals(listOf(4L, 3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun `all sortOrder equal falls back to createdAt`() {
        val folders = listOf(
            folder(id = 1, sortOrder = 0, createdAt = 300),
            folder(id = 2, sortOrder = 0, createdAt = 100),
            folder(id = 3, sortOrder = 0, createdAt = 200),
        )

        val sorted = folders.sortedForDisplay()

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun `empty list stays empty`() {
        assertEquals(emptyList<Folder>(), emptyList<Folder>().sortedForDisplay())
    }

    @Test
    fun `single element unchanged`() {
        val single = folder(id = 1, sortOrder = 5, createdAt = 100)
        assertEquals(listOf(single), listOf(single).sortedForDisplay())
    }
}