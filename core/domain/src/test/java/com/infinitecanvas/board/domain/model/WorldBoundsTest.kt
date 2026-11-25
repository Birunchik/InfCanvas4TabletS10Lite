package com.infinitecanvas.board.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorldBoundsTest {

    @Test
    fun `width height and center`() {
        val b = WorldBounds(0.0, 0.0, 10.0, 20.0)

        assertThat(b.width).isEqualTo(10.0)
        assertThat(b.height).isEqualTo(20.0)
        assertThat(b.centerX).isEqualTo(5.0)
        assertThat(b.centerY).isEqualTo(10.0)
    }

    @Test
    fun `intersects is true on overlap and on touch`() {
        val b = WorldBounds(0.0, 0.0, 10.0, 10.0)

        assertThat(b.intersects(WorldBounds(5.0, 5.0, 15.0, 15.0))).isTrue()
        assertThat(b.intersects(WorldBounds(10.0, 10.0, 20.0, 20.0))).isTrue() // касание углом
    }

    @Test
    fun `intersects is false when disjoint`() {
        val b = WorldBounds(0.0, 0.0, 10.0, 10.0)

        assertThat(b.intersects(WorldBounds(11.0, 11.0, 20.0, 20.0))).isFalse()
    }

    @Test
    fun `contains requires full coverage`() {
        val b = WorldBounds(0.0, 0.0, 10.0, 10.0)

        assertThat(b.contains(WorldBounds(2.0, 2.0, 8.0, 8.0))).isTrue()
        assertThat(b.contains(WorldBounds(2.0, 2.0, 12.0, 8.0))).isFalse()
    }

    @Test
    fun `fromCenter builds square bounds`() {
        val b = WorldBounds.fromCenter(cx = 5.0, cy = 5.0, radius = 3.0)

        assertThat(b).isEqualTo(WorldBounds(2.0, 2.0, 8.0, 8.0))
    }
}
