package com.infinitecanvas.board.presentation.canvas

import com.infinitecanvas.board.domain.model.BrushDefaults
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.testing.BrushFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты чистой функции calculateWidthForBrush (StrokeGeometryCache.kt).
 *
 * Формулы из прода:
 *   BALLPOINT   : base * (0.95 + p * 0.10)            → [0.95, 1.05] × base
 *   PENCIL      : base * (0.85 + p * 0.15)            → [0.85, 1.00] × base
 *   MARKER      : base * (0.98 + p * 0.05)            → [0.98, 1.03] × base
 *   FOUNTAIN    : base * pressureFactor * speedFactor
 *                 pressureFactor = 0.4 + p * 1.2      → [0.4, 1.6]
 *                 speedFactor    = 1 - min(speed/2.2,1) * 0.58 → [0.42, 1.0]
 *   HIGHLIGHTER : base * HIGHLIGHTER_WIDTH_MULTIPLIER (давление игнорируется)
 *   + клампинг: результат >= 0.5f
 */
class CalculateWidthForBrushTest {

    // --- BALLPOINT ---

    @Test
    fun `ballpoint low pressure is thinnest`() {
        val brush = BrushFixtures.brush(BrushType.BALLPOINT, baseSize = 100f)
        assertEquals(95f, calculateWidthForBrush(brush, pressure = 0f), EPS)
    }

    @Test
    fun `ballpoint high pressure is thickest`() {
        val brush = BrushFixtures.brush(BrushType.BALLPOINT, baseSize = 100f)
        assertEquals(105f, calculateWidthForBrush(brush, pressure = 1f), EPS)
    }

    @Test
    fun `ballpoint mid pressure is close to base`() {
        val brush = BrushFixtures.brush(BrushType.BALLPOINT, baseSize = 100f)
        assertEquals(100f, calculateWidthForBrush(brush, pressure = 0.5f), EPS)
    }

    // --- PENCIL ---

    @Test
    fun `pencil pressure scales width`() {
        val brush = BrushFixtures.brush(BrushType.PENCIL, baseSize = 100f)
        assertEquals(85f, calculateWidthForBrush(brush, pressure = 0f), EPS)
        assertEquals(100f, calculateWidthForBrush(brush, pressure = 1f), EPS)
    }

    // --- MARKER ---

    @Test
    fun `marker pressure scales width`() {
        val brush = BrushFixtures.brush(BrushType.MARKER, baseSize = 100f)
        assertEquals(98f, calculateWidthForBrush(brush, pressure = 0f), EPS)
        assertEquals(103f, calculateWidthForBrush(brush, pressure = 1f), EPS)
    }

    // --- FOUNTAIN: скорость и давление ---

    @Test
    fun `fountain low pressure at rest is thinnest`() {
        val brush = BrushFixtures.brush(BrushType.FOUNTAIN, baseSize = 100f)
        // pressureFactor = 0.4, speedFactor = 1.0
        assertEquals(40f, calculateWidthForBrush(brush, pressure = 0f, speedPxPerMs = 0f), EPS)
    }

    @Test
    fun `fountain high pressure at rest is thickest`() {
        val brush = BrushFixtures.brush(BrushType.FOUNTAIN, baseSize = 100f)
        // pressureFactor = 1.6, speedFactor = 1.0
        assertEquals(160f, calculateWidthForBrush(brush, pressure = 1f, speedPxPerMs = 0f), EPS)
    }

    @Test
    fun `fountain high speed shrinks width`() {
        val brush = BrushFixtures.brush(BrushType.FOUNTAIN, baseSize = 100f)
        // pressure=0.5 → pressureFactor = 1.0
        // speed=2.2 → speedNorm=1.0 → speedFactor=0.42
        assertEquals(42f, calculateWidthForBrush(brush, pressure = 0.5f, speedPxPerMs = 2.2f), EPS)
    }

    @Test
    fun `fountain speed above threshold saturates`() {
        val brush = BrushFixtures.brush(BrushType.FOUNTAIN, baseSize = 100f)
        val atThreshold = calculateWidthForBrush(brush, pressure = 0.5f, speedPxPerMs = 2.2f)
        val aboveThreshold = calculateWidthForBrush(brush, pressure = 0.5f, speedPxPerMs = 100f)
        assertEquals(atThreshold, aboveThreshold, EPS)
    }

    @Test
    fun `fountain slow speed is thinner than at rest`() {
        val brush = BrushFixtures.brush(BrushType.FOUNTAIN, baseSize = 100f)
        val atRest = calculateWidthForBrush(brush, pressure = 0.5f, speedPxPerMs = 0f)
        val slow = calculateWidthForBrush(brush, pressure = 0.5f, speedPxPerMs = 0.5f)
        assertTrue("Slow fountain pen должен быть тоньше, чем в покое", slow < atRest)
    }

    // --- HIGHLIGHTER ---

    @Test
    fun `highlighter ignores pressure uses width multiplier`() {
        val brush = BrushFixtures.brush(BrushType.HIGHLIGHTER, baseSize = 10f)
        val expected = 10f * BrushDefaults.HIGHLIGHTER_WIDTH_MULTIPLIER
        assertEquals(expected, calculateWidthForBrush(brush, pressure = 0f), EPS)
        assertEquals(expected, calculateWidthForBrush(brush, pressure = 0.5f), EPS)
        assertEquals(expected, calculateWidthForBrush(brush, pressure = 1f), EPS)
    }

    // --- Клампинг ---

    @Test
    fun `pressure below zero clamped to zero`() {
        val brush = BrushFixtures.brush(BrushType.BALLPOINT, baseSize = 100f)
        val atZero = calculateWidthForBrush(brush, pressure = 0f)
        val belowZero = calculateWidthForBrush(brush, pressure = -1f)
        assertEquals(atZero, belowZero, EPS)
    }

    @Test
    fun `pressure above one clamped to one`() {
        val brush = BrushFixtures.brush(BrushType.BALLPOINT, baseSize = 100f)
        val atOne = calculateWidthForBrush(brush, pressure = 1f)
        val aboveOne = calculateWidthForBrush(brush, pressure = 2f)
        assertEquals(atOne, aboveOne, EPS)
    }

    @Test
    fun `all brushes return at least half pixel`() {
        for (type in BrushType.entries) {
            val brush = BrushFixtures.brush(type, baseSize = 0.1f)
            assertTrue(
                "$type: width должен быть >= 0.5f, получено ${calculateWidthForBrush(brush, 0f)}",
                calculateWidthForBrush(brush, pressure = 0f) >= 0.5f
            )
        }
    }

    @Test
    fun `speed ignored for non-fountain brushes`() {
        for (type in BrushType.entries.filter { it != BrushType.FOUNTAIN }) {
            val brush = BrushFixtures.brush(type, baseSize = 100f)
            val atRest = calculateWidthForBrush(brush, pressure = 0.5f, speedPxPerMs = 0f)
            val fast = calculateWidthForBrush(brush, pressure = 0.5f, speedPxPerMs = 5f)
            assertEquals("$type должен игнорировать скорость", atRest, fast, EPS)
        }
    }

    private companion object {
        // Float-точность на базе 10..100 — 0.01 с большим запасом
        const val EPS = 0.01f
    }
}