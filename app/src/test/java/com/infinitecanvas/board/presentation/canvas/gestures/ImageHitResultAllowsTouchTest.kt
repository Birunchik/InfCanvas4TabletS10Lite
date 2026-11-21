package com.infinitecanvas.board.presentation.canvas.gestures

import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.testing.BoardImageFixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Таблица истинности для ImageHitResult.allowsTouchInteraction.
 *
 * Правило: любое из 5 полей non-null/true → true.
 * Используется в detectCanvasGestures для определения, перехватывать ли
 * жест или отдать в pan/zoom.
 */
class ImageHitResultAllowsTouchTest {

    private val img: BoardImage = BoardImageFixtures.image(
        id = 1L, isPinned = false, bounds = WorldBounds(0.0, 0.0, 100.0, 100.0)
    )

    @Test
    fun `all empty returns false`() {
        val r = ImageHitResult(
            selectedImg = null,
            hitCornerOnSelected = null,
            hitSelectedImgBody = false,
            hitPinnedImg = null,
            hitUnpinnedImg = null
        )
        assertFalse(r.allowsTouchInteraction)
    }

    @Test
    fun `selectedImg alone returns true`() {
        val r = ImageHitResult(
            selectedImg = img,
            hitCornerOnSelected = null,
            hitSelectedImgBody = false,
            hitPinnedImg = null,
            hitUnpinnedImg = null
        )
        assertTrue(r.allowsTouchInteraction)
    }

    @Test
    fun `corner alone returns true`() {
        val r = ImageHitResult(
            selectedImg = null,
            hitCornerOnSelected = ImageResizeCorner.TOP_LEFT,
            hitSelectedImgBody = false,
            hitPinnedImg = null,
            hitUnpinnedImg = null
        )
        assertTrue(r.allowsTouchInteraction)
    }

    @Test
    fun `selected body alone returns true`() {
        val r = ImageHitResult(
            selectedImg = null,
            hitCornerOnSelected = null,
            hitSelectedImgBody = true,
            hitPinnedImg = null,
            hitUnpinnedImg = null
        )
        assertTrue(r.allowsTouchInteraction)
    }

    @Test
    fun `pinned image alone returns true`() {
        val r = ImageHitResult(
            selectedImg = null,
            hitCornerOnSelected = null,
            hitSelectedImgBody = false,
            hitPinnedImg = img,
            hitUnpinnedImg = null
        )
        assertTrue(r.allowsTouchInteraction)
    }

    @Test
    fun `unpinned image alone returns true`() {
        val r = ImageHitResult(
            selectedImg = null,
            hitCornerOnSelected = null,
            hitSelectedImgBody = false,
            hitPinnedImg = null,
            hitUnpinnedImg = img
        )
        assertTrue(r.allowsTouchInteraction)
    }

    @Test
    fun `all set returns true`() {
        val r = ImageHitResult(
            selectedImg = img,
            hitCornerOnSelected = ImageResizeCorner.BOTTOM_RIGHT,
            hitSelectedImgBody = true,
            hitPinnedImg = img,
            hitUnpinnedImg = img
        )
        assertTrue(r.allowsTouchInteraction)
    }
}