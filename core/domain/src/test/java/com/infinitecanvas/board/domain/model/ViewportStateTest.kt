package com.infinitecanvas.board.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ViewportStateTest {

    @Test
    fun `screenToWorld subtracts offset and divides by scale`() {
        val vp = ViewportState(offsetX = 10.0, offsetY = 20.0, scale = 2.0)

        assertThat(vp.screenToWorldX(30f)).isEqualTo(10.0)
        assertThat(vp.screenToWorldY(40f)).isEqualTo(10.0)
    }

    @Test
    fun `worldToScreen multiplies by scale and adds offset`() {
        val vp = ViewportState(offsetX = 10.0, offsetY = 20.0, scale = 2.0)

        assertThat(vp.worldToScreenX(10.0)).isEqualTo(30f)
        assertThat(vp.worldToScreenY(10.0)).isEqualTo(40f)
    }

    @Test
    fun `round-trip preserves world coordinates`() {
        val vp = ViewportState(offsetX = -5.0, offsetY = 7.0, scale = 1.5)

        val wx = vp.screenToWorldX(vp.worldToScreenX(12.0))
        val wy = vp.screenToWorldY(vp.worldToScreenY(-4.0))

        assertThat(wx).isWithin(1e-4).of(12.0)
        assertThat(wy).isWithin(1e-4).of(-4.0)
    }
}
