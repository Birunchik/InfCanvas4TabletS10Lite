package com.infinitecanvas.board.testing

import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.WorldBounds
import io.mockk.every
import io.mockk.mockk

/**
 * Мок BoardImage: только три поля нужны evaluateHit / toScreenBounds.
 *
 * Мок, а не реальный конструктор — чтобы не зависеть от эволюции
 * полей BoardImage (fileName, rotation, transform и т.п.).
 */
object BoardImageFixtures {

    fun image(
        id: Long,
        isPinned: Boolean,
        bounds: WorldBounds
    ): BoardImage {
        val img = mockk<BoardImage>()
        every { img.id } returns id
        every { img.isPinned } returns isPinned
        every { img.bounds } returns bounds
        return img
    }
}