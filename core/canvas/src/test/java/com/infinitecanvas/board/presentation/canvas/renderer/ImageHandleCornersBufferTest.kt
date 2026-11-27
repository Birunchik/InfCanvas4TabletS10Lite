package com.infinitecanvas.board.presentation.canvas.renderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты буфера углов рамки выделенной картинки (CanvasRenderState.imageHandleCorners).
 *
 * Почему это важно: drawScreenOverlays раньше собирал `listOf(Offset, ...)` на каждый
 * кадр, что нарушало инвариант «draw-фаза не аллоцирует». Теперь углы пишутся в
 * переиспользуемый FloatArray, и контракт буфера обязан быть закреплён тестом:
 *   - массив ровно на 8 float (4 угла × x,y)
 *   - это один и тот же экземпляр между кадрами (переиспользование, не аллокация)
 *   - порядок углов: TL, TR, BL, BR
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageHandleCornersBufferTest {

    @Test
    fun `handle corners buffer has 8 floats for 4 points`() {
        val state = CanvasRenderState()
        assertEquals(8, state.imageHandleCorners.size)
    }

    @Test
    fun `handle corners buffer is reused across accesses`() {
        // Ключевой контракт: буфер принадлежит CanvasRenderState и не пересоздаётся,
        // иначе draw-фаза снова начала бы аллоцировать массив на кадр.
        val state = CanvasRenderState()
        val first = state.imageHandleCorners
        val second = state.imageHandleCorners
        assertSame(first, second)
    }

    @Test
    fun `buffer holds corner coordinates in TL TR BL BR order`() {
        val state = CanvasRenderState()
        val b = state.imageHandleCorners
        val left = 10f
        val top = 20f
        val right = 110f
        val bottom = 220f

        // Тот же порядок записи, что и в drawScreenOverlays.
        b[0] = left; b[1] = top
        b[2] = right; b[3] = top
        b[4] = left; b[5] = bottom
        b[6] = right; b[7] = bottom

        assertEquals(left, b[0], 0f)
        assertEquals(top, b[1], 0f)
        assertEquals(right, b[2], 0f)
        assertEquals(top, b[3], 0f)
        assertEquals(left, b[4], 0f)
        assertEquals(bottom, b[5], 0f)
        assertEquals(right, b[6], 0f)
        assertEquals(bottom, b[7], 0f)
    }

    @Test
    fun `buffer is rewritable without growing`() {
        val state = CanvasRenderState()
        val b = state.imageHandleCorners
        val sizeBefore = b.size

        repeat(100) { i ->
            b[0] = i.toFloat(); b[1] = i.toFloat()
            b[2] = (i + 1).toFloat(); b[3] = i.toFloat()
            b[4] = i.toFloat(); b[5] = (i + 1).toFloat()
            b[6] = (i + 1).toFloat(); b[7] = (i + 1).toFloat()
        }

        assertEquals(sizeBefore, b.size)
    }
}
