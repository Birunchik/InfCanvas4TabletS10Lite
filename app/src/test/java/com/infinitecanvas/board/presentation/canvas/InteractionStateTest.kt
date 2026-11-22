package com.infinitecanvas.board.presentation.canvas

import com.infinitecanvas.board.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InteractionStateTest {

    @Test
    fun `initial state is empty and zero`() {
        val state = CanvasInteractionState()
        assertTrue(state.activePoints.isEmpty())
        assertTrue(state.lassoPoints.isEmpty())
        assertEquals(0.0, state.selectionDragDx, 1e-6)
        assertEquals(0.0, state.selectionDragDy, 1e-6)
    }

    @Test
    fun `reset clears all points and drag offsets`() {
        val state = CanvasInteractionState().apply {
            activePoints.add(StrokePoint(1.0, 2.0, 0.5f, 100L))
            lassoPoints.add(StrokePoint(3.0, 4.0, 0.5f, 200L))
            selectionDragDx = 10.0
            selectionDragDy = 20.0
        }

        state.reset()

        assertTrue(state.activePoints.isEmpty())
        assertTrue(state.lassoPoints.isEmpty())
        assertEquals(0.0, state.selectionDragDx, 1e-6)
        assertEquals(0.0, state.selectionDragDy, 1e-6)
    }
}
