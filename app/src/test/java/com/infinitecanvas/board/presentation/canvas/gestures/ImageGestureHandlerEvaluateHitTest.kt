package com.infinitecanvas.board.presentation.canvas.gestures

import androidx.compose.ui.geometry.Offset
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.testing.BoardImageFixtures
import com.infinitecanvas.board.testing.ViewportMockFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Тесты ImageGestureHandler.evaluateHit (ImageGestureHandler.kt).
 *
 * Покрываемые контракты:
 *   - selectedImg ищется по selectedImageId()
 *   - hitSelectedBody и cornerHit считаются в screen-координатах
 *   - hitCorner: радиус 28px в screen (не world) — не растёт при зуме
 *   - pinned hit: screen-координаты напрямую (bounds уже screen)
 *   - unpinned hit: world-координаты (bounds в world, конверсия не нужна)
 *   - overlapping → lastOrNull (последний в списке = верхний слой)
 *   - toScreenBounds: pinned → bounds как есть; unpinned → worldToScreen
 */
class ImageGestureHandlerEvaluateHitTest {

    private fun handler(
        vp: com.infinitecanvas.board.domain.model.ViewportState,
        images: List<BoardImage>,
        selectedId: Long? = null
    ) = ImageGestureHandler(
        viewportState = { vp },
        images = { images },
        selectedImageId = { selectedId },
        onImageSelected = {},
        onUpdateImageBounds = { _, _ -> },
        onStateChanged = {}
    )

    // ---------- пустой state ----------

    @Test
    fun `no images selected and no hit returns empty result`() {
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val h = handler(vp, images = emptyList(), selectedId = null)

        val r = h.evaluateHit(Offset(50f, 50f), worldX = 50.0, worldY = 50.0, vp = vp)

        assertNull(r.selectedImg)
        assertNull(r.hitCornerOnSelected)
        assertEquals(false, r.hitSelectedImgBody)
        assertNull(r.hitPinnedImg)
        assertNull(r.hitUnpinnedImg)
    }

    // ---------- selected image: corner / body ----------

    @Test
    fun `selected image corner TL detected`() {
        // isPinned=true → bounds уже screen. TL=(100,100), клик (110,110) — dist≈14<28
        val img = BoardImageFixtures.image(42L, isPinned = true, bounds = WorldBounds(100.0, 100.0, 200.0, 200.0))
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val h = handler(vp, listOf(img), selectedId = 42L)

        val r = h.evaluateHit(Offset(110f, 110f), worldX = 110.0, worldY = 110.0, vp = vp)

        assertSame(img, r.selectedImg)
        assertEquals(ImageResizeCorner.TOP_LEFT, r.hitCornerOnSelected)
        assertEquals(true, r.hitSelectedImgBody)
    }

    @Test
    fun `selected image corner TR detected`() {
        val img = BoardImageFixtures.image(42L, isPinned = true, bounds = WorldBounds(100.0, 100.0, 200.0, 200.0))
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val h = handler(vp, listOf(img), selectedId = 42L)

        val r = h.evaluateHit(Offset(190f, 110f), worldX = 190.0, worldY = 110.0, vp = vp)

        assertEquals(ImageResizeCorner.TOP_RIGHT, r.hitCornerOnSelected)
    }

    @Test
    fun `selected image corner BL detected`() {
        val img = BoardImageFixtures.image(42L, isPinned = true, bounds = WorldBounds(100.0, 100.0, 200.0, 200.0))
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val h = handler(vp, listOf(img), selectedId = 42L)

        val r = h.evaluateHit(Offset(110f, 190f), worldX = 110.0, worldY = 190.0, vp = vp)

        assertEquals(ImageResizeCorner.BOTTOM_LEFT, r.hitCornerOnSelected)
    }

    @Test
    fun `selected image corner BR detected`() {
        val img = BoardImageFixtures.image(42L, isPinned = true, bounds = WorldBounds(100.0, 100.0, 200.0, 200.0))
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val h = handler(vp, listOf(img), selectedId = 42L)

        val r = h.evaluateHit(Offset(190f, 190f), worldX = 190.0, worldY = 190.0, vp = vp)

        assertEquals(ImageResizeCorner.BOTTOM_RIGHT, r.hitCornerOnSelected)
    }

    @Test
    fun `selected image body hit without corner`() {
        // Центр (150,150): dist до всех углов ≈ 70.7 > 28
        val img = BoardImageFixtures.image(42L, isPinned = true, bounds = WorldBounds(100.0, 100.0, 200.0, 200.0))
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val h = handler(vp, listOf(img), selectedId = 42L)

        val r = h.evaluateHit(Offset(150f, 150f), worldX = 150.0, worldY = 150.0, vp = vp)

        assertSame(img, r.selectedImg)
        assertNull(r.hitCornerOnSelected)
        assertEquals(true, r.hitSelectedImgBody)
    }

    @Test
    fun `corner radius is 28px in screen even at zoom`() {
        // scale=2: изображение в world(50..100) → screen(100..200).
        // Corner TL в screen = (100,100). Клик (120,120) — dist=28.28>28 → не corner.
        // Клик (110,110) — dist=14.14<28 → corner.
        val img = BoardImageFixtures.image(42L, isPinned = false, bounds = WorldBounds(50.0, 50.0, 100.0, 100.0))
        val vp = ViewportMockFactory.viewport(scale = 2.0)
        val h = handler(vp, listOf(img), selectedId = 42L)

        val nearCorner = h.evaluateHit(Offset(110f, 110f), worldX = 55.0, worldY = 55.0, vp = vp)
        assertEquals(
            "радиус угла в screen, не масштабируется зумом",
            ImageResizeCorner.TOP_LEFT, nearCorner.hitCornerOnSelected
        )

        val justBeyond = h.evaluateHit(Offset(130f, 130f), worldX = 65.0, worldY = 65.0, vp = vp)
        assertNull("за пределами 28px — не corner", justBeyond.hitCornerOnSelected)
    }

    @Test
    fun `selected image miss outside bounds`() {
        val img = BoardImageFixtures.image(42L, isPinned = true, bounds = WorldBounds(100.0, 100.0, 200.0, 200.0))
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val h = handler(vp, listOf(img), selectedId = 42L)

        val r = h.evaluateHit(Offset(50f, 50f), worldX = 50.0, worldY = 50.0, vp = vp)

        assertSame(img, r.selectedImg) // selected по id, независимо от клика
        assertNull(r.hitCornerOnSelected)
        assertEquals(false, r.hitSelectedImgBody)
    }

    // ---------- pinned vs unpinned: разные координатные системы ----------

    @Test
    fun `unpinned image hit in world space`() {
        // scale=2: world(0..100) → screen(0..200).
        // Клик screen(150,150) → world(75,75) — внутри world bounds → hitUnpinned.
        val img = BoardImageFixtures.image(1L, isPinned = false, bounds = WorldBounds(0.0, 0.0, 100.0, 100.0))
        val vp = ViewportMockFactory.viewport(scale = 2.0)
        val h = handler(vp, listOf(img))

        val r = h.evaluateHit(Offset(150f, 150f), worldX = 75.0, worldY = 75.0, vp = vp)

        assertSame(img, r.hitUnpinnedImg)
    }

    @Test
    fun `unpinned image missed by screen coords if world mismatch`() {
        // Тот же img, но клик вне world bounds: screen(400,400) → world(200,200) — вне (0..100)
        val img = BoardImageFixtures.image(1L, isPinned = false, bounds = WorldBounds(0.0, 0.0, 100.0, 100.0))
        val vp = ViewportMockFactory.viewport(scale = 2.0)
        val h = handler(vp, listOf(img))

        val r = h.evaluateHit(Offset(400f, 400f), worldX = 200.0, worldY = 200.0, vp = vp)

        assertNull(r.hitUnpinnedImg)
    }

    @Test
    fun `pinned image hit in screen space`() {
        // pinned: bounds в screen напрямую. Клик screen(50,50) при scale=2 → попадает.
        val img = BoardImageFixtures.image(1L, isPinned = true, bounds = WorldBounds(0.0, 0.0, 100.0, 100.0))
        val vp = ViewportMockFactory.viewport(scale = 2.0)
        val h = handler(vp, listOf(img))

        val r = h.evaluateHit(Offset(50f, 50f), worldX = 25.0, worldY = 25.0, vp = vp)

        assertSame(img, r.hitPinnedImg)
    }

    @Test
    fun `pinned image missed by world-only hit`() {
        // scale=2, клик screen(150,150) → world(75,75) в (0..100) world, НО
        // screen 150 > bounds.maxX 100 → мимо pinned.
        val img = BoardImageFixtures.image(1L, isPinned = true, bounds = WorldBounds(0.0, 0.0, 100.0, 100.0))
        val vp = ViewportMockFactory.viewport(scale = 2.0)
        val h = handler(vp, listOf(img))

        val r = h.evaluateHit(Offset(150f, 150f), worldX = 75.0, worldY = 75.0, vp = vp)

        assertNull("pinned в screen-системе, screen 150 вне (0..100)", r.hitPinnedImg)
    }

    // ---------- перекрытие: lastOrNull ----------

    @Test
    fun `overlapping unpinned picks last in list`() {
        val bottom = BoardImageFixtures.image(1L, isPinned = false, bounds = WorldBounds(0.0, 0.0, 100.0, 100.0))
        val top = BoardImageFixtures.image(2L, isPinned = false, bounds = WorldBounds(0.0, 0.0, 100.0, 100.0))
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val h = handler(vp, listOf(bottom, top))

        val r = h.evaluateHit(Offset(50f, 50f), worldX = 50.0, worldY = 50.0, vp = vp)

        assertSame("lastOrNull → верхний слой", top, r.hitUnpinnedImg)
    }

    @Test
    fun `overlapping pinned picks last in list`() {
        val bottom = BoardImageFixtures.image(1L, isPinned = true, bounds = WorldBounds(0.0, 0.0, 100.0, 100.0))
        val top = BoardImageFixtures.image(2L, isPinned = true, bounds = WorldBounds(0.0, 0.0, 100.0, 100.0))
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val h = handler(vp, listOf(bottom, top))

        val r = h.evaluateHit(Offset(50f, 50f), worldX = 50.0, worldY = 50.0, vp = vp)

        assertSame(top, r.hitPinnedImg)
    }

    @Test
    fun `pinned and unpinned same location both hit independently`() {
        // Pinned и unpinned могут попасть одновременно — это два разных поля.
        // Приоритет между ними решается в tryHandleImmediateGesture, не здесь.
        val pinned = BoardImageFixtures.image(1L, isPinned = true, bounds = WorldBounds(0.0, 0.0, 100.0, 100.0))
        val unpinned = BoardImageFixtures.image(2L, isPinned = false, bounds = WorldBounds(0.0, 0.0, 100.0, 100.0))
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val h = handler(vp, listOf(pinned, unpinned))

        val r = h.evaluateHit(Offset(50f, 50f), worldX = 50.0, worldY = 50.0, vp = vp)

        assertSame(pinned, r.hitPinnedImg)
        assertSame(unpinned, r.hitUnpinnedImg)
    }
}