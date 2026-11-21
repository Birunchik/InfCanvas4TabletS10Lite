package com.infinitecanvas.board.testing

import com.infinitecanvas.board.domain.model.ViewportState
import io.mockk.every
import io.mockk.mockk

/**
 * Единая фабрика ViewportState-mock'ов для тестов жестов.
 *
 * Математика: screen = world * scale + offset, world = (screen - offset) / scale.
 * Совпадает с формулами ViewportState.worldToScreenX / screenToWorldX.
 *
 * При scale=1, offset=0: world == screen (удобно для тестов image-hit).
 * При scale=2: world=100 → screen=200 (видно разницу координатных систем).
 */
object ViewportMockFactory {

    fun viewport(
        scale: Double = 1.0,
        offsetX: Double = 0.0,
        offsetY: Double = 0.0
    ): ViewportState {
        val vp = mockk<ViewportState>()
        every { vp.scale } returns scale
        every { vp.screenToWorldX(any()) } answers {
            (firstArg<Float>().toDouble() - offsetX) / scale
        }
        every { vp.screenToWorldY(any()) } answers {
            (firstArg<Float>().toDouble() - offsetY) / scale
        }
        every { vp.worldToScreenX(any()) } answers {
            (firstArg<Double>() * scale + offsetX).toFloat()
        }
        every { vp.worldToScreenY(any()) } answers {
            (firstArg<Double>() * scale + offsetY).toFloat()
        }
        return vp
    }
}