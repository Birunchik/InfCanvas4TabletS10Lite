package com.infinitecanvas.board.presentation.canvas.gestures

import androidx.compose.ui.geometry.Offset
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.spatial.Quadtree
import com.infinitecanvas.board.testing.ViewportMockFactory
import io.mockk.MockKAnswerScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты eraseAtScreenPosition (CanvasGestures.kt).
 *
 * Контракты:
 *   - worldEraserRadius = 18 / max(scale, 0.15)
 *   - если findHitStrokeIds вернул непусто → onErase(hitIds)
 *   - если пусто → onErase НЕ вызывается
 *   - screenToWorldX/Y вызываются с screen-позицией
 */
class EraseAtScreenPositionTest {

    private fun mockQuadtreeWithHits(hits: Set<Long>): Quadtree {
        val qt = mockk<Quadtree>()
        every { qt.findHitStrokeIds(any(), any(), any(), any(), any()) } answers {
            hitIdsBuffer().also { it.addAll(hits) }
        }
        return qt
    }

    @Test
    fun `hit outside radius is not erased`() {
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val qt = mockQuadtreeWithHits(emptySet())
        val eraseCalls = mutableListOf<Set<Long>>()

        eraseAtScreenPosition(
            screenPos = Offset(50f, 50f),
            viewport = vp,
            quadtree = qt,
            candidatesBuffer = mutableListOf<Stroke>(),
            hitIdsBuffer = mutableSetOf(),
            onErase = { eraseCalls += it }
        )

        assertTrue("onErase не должен быть вызван при пустом hitIds", eraseCalls.isEmpty())
    }

    @Test
    fun `hit within radius triggers callback with hit ids`() {
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val qt = mockQuadtreeWithHits(setOf(1L, 2L))
        val eraseCalls = mutableListOf<Set<Long>>()

        eraseAtScreenPosition(
            screenPos = Offset(50f, 50f),
            viewport = vp,
            quadtree = qt,
            candidatesBuffer = mutableListOf(),
            hitIdsBuffer = mutableSetOf(),
            onErase = { eraseCalls += it }
        )

        assertEquals(1, eraseCalls.size)
        assertEquals(setOf(1L, 2L), eraseCalls[0])
    }

    @Test
    fun `world radius is 18 px divided by scale`() {
        val vp = ViewportMockFactory.viewport(scale = 2.0)
        val qt = mockk<Quadtree>()
        val radiusSlot = slot<Double>()
        every { qt.findHitStrokeIds(any(), any(), capture(radiusSlot), any(), any()) } answers {
            hitIdsBuffer()
        }

        eraseAtScreenPosition(
            screenPos = Offset(50f, 50f),
            viewport = vp,
            quadtree = qt,
            candidatesBuffer = mutableListOf(),
            hitIdsBuffer = mutableSetOf(),
            onErase = {}
        )

        assertEquals("scale=2 → 18/2 = 9.0", 9.0, radiusSlot.captured, EPS)
    }

    @Test
    fun `world radius capped at scale 0 point 15`() {
        // coerceAtLeast(0.15): при scale=0.1 → используем 0.15 → 18/0.15 = 120.0
        val vp = ViewportMockFactory.viewport(scale = 0.1)
        val qt = mockk<Quadtree>()
        val radiusSlot = slot<Double>()
        every { qt.findHitStrokeIds(any(), any(), capture(radiusSlot), any(), any()) } answers {
            hitIdsBuffer()
        }

        eraseAtScreenPosition(
            screenPos = Offset(50f, 50f),
            viewport = vp,
            quadtree = qt,
            candidatesBuffer = mutableListOf(),
            hitIdsBuffer = mutableSetOf(),
            onErase = {}
        )

        assertEquals("scale=0.1 → coerce до 0.15 → 18/0.15 = 120.0", 120.0, radiusSlot.captured, EPS)
    }

    @Test
    fun `screen position converted to world coordinates`() {
        val vp = ViewportMockFactory.viewport(scale = 2.0, offsetX = 10.0, offsetY = 20.0)
        val qt = mockk<Quadtree>()
        val worldXSlot = slot<Double>()
        val worldYSlot = slot<Double>()
        every {
            qt.findHitStrokeIds(capture(worldXSlot), capture(worldYSlot), any(), any(), any())
        } answers {
            hitIdsBuffer()
        }

        eraseAtScreenPosition(
            screenPos = Offset(50f, 100f),
            viewport = vp,
            quadtree = qt,
            candidatesBuffer = mutableListOf(),
            hitIdsBuffer = mutableSetOf(),
            onErase = {}
        )

        // world = (screen - offset) / scale
        assertEquals((50.0 - 10.0) / 2.0, worldXSlot.captured, EPS) // 20.0
        assertEquals((100.0 - 20.0) / 2.0, worldYSlot.captured, EPS) // 40.0
    }

    @Test
    fun `candidates and hit buffers are passed through`() {
        // Контракт: buffers из CanvasRenderState переиспользуются — не пересоздаются
        val vp = ViewportMockFactory.viewport(scale = 1.0)
        val qt = mockk<Quadtree>()
        val expectedCandidates = mutableListOf<Stroke>()
        val expectedHitBuffer = mutableSetOf<Long>()
        val candidatesSlot = slot<MutableList<Stroke>>()
        val hitBufferSlot = slot<MutableSet<Long>>()
        every {
            qt.findHitStrokeIds(any(), any(), any(), capture(candidatesSlot), capture(hitBufferSlot))
        } answers {
            hitIdsBuffer()
        }

        eraseAtScreenPosition(
            screenPos = Offset(0f, 0f),
            viewport = vp,
            quadtree = qt,
            candidatesBuffer = expectedCandidates,
            hitIdsBuffer = expectedHitBuffer,
            onErase = {}
        )

        assertTrue(
            "candidatesBuffer должен быть тот же instance",
            candidatesSlot.captured === expectedCandidates
        )
        assertTrue(
            "hitIdsBuffer должен быть тот же instance",
            hitBufferSlot.captured === expectedHitBuffer
        )
    }

    private companion object {
        const val EPS = 1e-6
    }
}

@Suppress("UNCHECKED_CAST")
private fun MockKAnswerScope<*, *>.hitIdsBuffer(): MutableSet<Long> =
    args[4] as MutableSet<Long>