package com.infinitecanvas.board.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BoardImageTest {

    @Test
    fun `width and height derive from bounds`() {
        val img = BoardImage(id = 1L, fileName = "a.webp", bounds = WorldBounds(0.0, 0.0, 100.0, 50.0))

        assertThat(img.width).isEqualTo(100.0)
        assertThat(img.height).isEqualTo(50.0)
    }

    @Test
    fun `resized returns copy with new bounds keeping other fields`() {
        val img = BoardImage(
            id = 1L,
            fileName = "a.webp",
            bounds = WorldBounds(0.0, 0.0, 10.0, 10.0),
            zIndex = 3,
            isPinned = true
        )

        val resized = img.resized(WorldBounds(1.0, 1.0, 5.0, 5.0))

        assertThat(resized.bounds).isEqualTo(WorldBounds(1.0, 1.0, 5.0, 5.0))
        assertThat(resized.id).isEqualTo(1L)
        assertThat(resized.fileName).isEqualTo("a.webp")
        assertThat(resized.zIndex).isEqualTo(3)
        assertThat(resized.isPinned).isTrue()
    }
}
