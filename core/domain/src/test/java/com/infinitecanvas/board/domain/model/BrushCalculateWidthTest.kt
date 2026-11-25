package com.infinitecanvas.board.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrushCalculateWidthTest {

    private fun width(type: BrushType, base: Float, pressure: Float, speed: Float = 0f): Float =
        Brush(type = type, baseSize = base, colorArgb = 0, opacity = 1f)
            .calculateWidth(pressure, speed)

    @Test
    fun `ballpoint scales with pressure`() {
        assertThat(width(BrushType.BALLPOINT, 100f, 0f)).isWithin(0.001f).of(95f)
        assertThat(width(BrushType.BALLPOINT, 100f, 0.5f)).isWithin(0.001f).of(100f)
        assertThat(width(BrushType.BALLPOINT, 100f, 1f)).isWithin(0.001f).of(105f)
    }

    @Test
    fun `marker scales with pressure`() {
        assertThat(width(BrushType.MARKER, 100f, 0f)).isWithin(0.001f).of(98f)
        assertThat(width(BrushType.MARKER, 100f, 1f)).isWithin(0.001f).of(103f)
    }

    @Test
    fun `highlighter ignores pressure and uses fixed multiplier`() {
        assertThat(width(BrushType.HIGHLIGHTER, 100f, 0f)).isWithin(0.001f).of(600f)
        assertThat(width(BrushType.HIGHLIGHTER, 100f, 0.5f)).isWithin(0.001f).of(600f)
        assertThat(width(BrushType.HIGHLIGHTER, 100f, 1f)).isWithin(0.001f).of(600f)
    }

    @Test
    fun `fountain shrinks with speed`() {
        assertThat(width(BrushType.FOUNTAIN, 100f, 0.5f, 0f)).isWithin(0.001f).of(100f)
        // speed >= 2.2 → speedFactor = 0.42
        assertThat(width(BrushType.FOUNTAIN, 100f, 0.5f, 2.2f)).isWithin(0.001f).of(42f)
    }

    @Test
    fun `fountain scales with pressure`() {
        assertThat(width(BrushType.FOUNTAIN, 100f, 0f)).isWithin(0.001f).of(40f)
        assertThat(width(BrushType.FOUNTAIN, 100f, 1f)).isWithin(0.001f).of(160f)
    }

    @Test
    fun `result never drops below floor`() {
        assertThat(width(BrushType.BALLPOINT, 0.1f, 0f)).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `pressure is clamped to 0-1`() {
        // ниже 0 → как 0; выше 1 → как 1
        assertThat(width(BrushType.BALLPOINT, 100f, -1f)).isWithin(0.001f).of(95f)
        assertThat(width(BrushType.BALLPOINT, 100f, 2f)).isWithin(0.001f).of(105f)
    }
}
