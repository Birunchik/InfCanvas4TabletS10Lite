package com.infinitecanvas.board.presentation.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.testing.BrushFixtures
import com.infinitecanvas.board.testing.StrokeRealFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты buildGeometry (StrokeGeometryCache.kt).
 *
 * Покрываемые контракты:
 *   - n=1, non-highlighter → singleDotCenter + пустой path
 *   - n=1, highlighter → chisel-path, singleDotCenter = null
 *   - n≥2 → непустой path, singleDotCenter = null
 *   - highlighter: bounds расширены на effectiveWidth*0.85 = baseSize*6*0.85
 *   - highlighter: alpha клампится в [0.35, 0.45]
 *   - non-highlighter: alpha = brush.opacity (без клампа)
 *   - color сохраняет brush.colorArgb
 *   - targetPath.reset() — старый мусор не остаётся
 *   - FOUNTAIN: path непуст, variableSegments пуст, strokeWidth = 0
 */

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildGeometryTest {

    private val bounds = WorldBounds(0.0, 0.0, 100.0, 100.0)

    // ---------- n = 1 ----------

    @Test
    fun `singlePoint_nonHighlighter_returnsDotCenter`() {
        val brush = BrushFixtures.brush(BrushType.BALLPOINT, baseSize = 10f)
        val stroke = StrokeRealFixtures.withExplicitBounds(
            id = 1,
            points = listOf(StrokePoint(50.0, 60.0, pressure = 0.5f)),
            brush = brush,
            bounds = bounds
        )

        val cached = buildGeometry(stroke, Path())

        assertNotNull("singleDotCenter должен быть задан", cached.singleDotCenter)
        assertEquals(50f, cached.singleDotCenter!!.x, EPS)
        assertEquals(60f, cached.singleDotCenter!!.y, EPS)
        // BALLPOINT baseSize=10, pressure=0.5 → width=10 → radius=5
        assertEquals(5f, cached.singleDotRadius, EPS)
        assertFalse(cached.isHighlighter)
    }

    @Test
    fun `singlePoint_nonHighlighter_pathIsEmpty`() {
        val brush = BrushFixtures.brush(BrushType.BALLPOINT)
        val stroke = StrokeRealFixtures.withExplicitBounds(
            id = 1,
            points = listOf(StrokePoint(50.0, 50.0, 0.5f)),
            brush = brush,
            bounds = bounds
        )
        val path = Path()

        buildGeometry(stroke, path)

        assertTrue("path для single-dot non-highlighter должен остаться пустым", path.isEmpty)
    }

    @Test
    fun `singlePoint_highlighter_returnsChiselPath`() {
        val brush = BrushFixtures.brush(BrushType.HIGHLIGHTER, baseSize = 10f)
        val stroke = StrokeRealFixtures.withExplicitBounds(
            id = 1,
            points = listOf(StrokePoint(50.0, 50.0, 0.5f)),
            brush = brush,
            bounds = bounds
        )
        val path = Path()

        val cached = buildGeometry(stroke, path)

        assertNull("highlighter рисуется path, а не точкой", cached.singleDotCenter)
        assertTrue(cached.isHighlighter)
        assertFalse("highlighter одиночная точка → chisel path", path.isEmpty)
    }

    // ---------- n = 2: обычные кисти ----------

    @Test
    fun `twoPoints_ballpoint_buildsPathWithoutDot`() {
        val brush = BrushFixtures.brush(BrushType.BALLPOINT, baseSize = 10f)
        val stroke = StrokeRealFixtures.created(1, 10.0 to 10.0, 90.0 to 90.0, brush = brush)
        val path = Path()

        val cached = buildGeometry(stroke, path)

        assertNull(cached.singleDotCenter)
        assertFalse(cached.isHighlighter)
        assertFalse(path.isEmpty)
        assertTrue("BALLPOINT strokeWidth должен быть > 0", cached.strokeWidth > 0f)
    }

    @Test
    fun `twoPoints_marker_buildsPath`() {
        val brush = BrushFixtures.brush(BrushType.MARKER, baseSize = 10f)
        val stroke = StrokeRealFixtures.created(1, 10.0 to 10.0, 90.0 to 90.0, brush = brush)

        val cached = buildGeometry(stroke, Path())

        assertFalse(cached.path.isEmpty)
        assertNull(cached.singleDotCenter)
        assertFalse(cached.isHighlighter)
    }

    @Test
    fun `twoPoints_pencil_buildsPath`() {
        val brush = BrushFixtures.brush(BrushType.PENCIL, baseSize = 10f)
        val stroke = StrokeRealFixtures.created(1, 10.0 to 10.0, 90.0 to 90.0, brush = brush)

        val cached = buildGeometry(stroke, Path())

        assertFalse(cached.path.isEmpty)
        assertNull(cached.singleDotCenter)
        assertFalse(cached.isHighlighter)
    }

    @Test
    fun `twoPoints_fountain_buildsVariableOutlinePath`() {
        val brush = BrushFixtures.brush(BrushType.FOUNTAIN, baseSize = 10f)
        val stroke = StrokeRealFixtures.created(1, 10.0 to 10.0, 90.0 to 90.0, brush = brush)

        val cached = buildGeometry(stroke, Path())

        assertFalse(cached.path.isEmpty)
        // По текущему коду FOUNTAIN отдаёт variableSegments = emptyList (не quantized)
        assertTrue(cached.variableSegments.isEmpty())
        assertEquals("FOUNTAIN: strokeWidth = 0f, рисуется path'ом", 0f, cached.strokeWidth, EPS)
    }

    // ---------- highlighter: bounds ----------

    @Test
    fun `highlighter_expandsBoundsByWidthMultiplier`() {
        // buildGeometry для highlighter:
        //   effectiveWidth = calculateWidthForBrush(brush, 0.5f, 0f) = baseSize * 6.0
        //   extraPad = effectiveWidth * 0.85 = baseSize * 5.1
        // Ожидаемое расширение — независимо от исходных bounds штриха.
        val baseSize = 10f
        val extraPad = baseSize * 6f * 0.85f // 51.0

        val brush = BrushFixtures.brush(BrushType.HIGHLIGHTER, baseSize = baseSize)
        val originalBounds = WorldBounds(10.0, 20.0, 110.0, 120.0)
        val stroke = StrokeRealFixtures.withExplicitBounds(
            id = 1,
            points = StrokeRealFixtures.points(50.0 to 60.0, 70.0 to 80.0),
            brush = brush,
            bounds = originalBounds
        )

        val cached = buildGeometry(stroke, Path())

        assertEquals(10.0 - extraPad, cached.bounds.minX, EPS_BOUNDS)
        assertEquals(20.0 - extraPad, cached.bounds.minY, EPS_BOUNDS)
        assertEquals(110.0 + extraPad, cached.bounds.maxX, EPS_BOUNDS)
        assertEquals(120.0 + extraPad, cached.bounds.maxY, EPS_BOUNDS)
    }

    @Test
    fun `nonHighlighter_boundsEqualStrokeBounds`() {
        for (type in listOf(BrushType.BALLPOINT, BrushType.MARKER, BrushType.PENCIL, BrushType.FOUNTAIN)) {
            val brush = BrushFixtures.brush(type, baseSize = 10f)
            val originalBounds = WorldBounds(10.0, 20.0, 110.0, 120.0)
            val stroke = StrokeRealFixtures.withExplicitBounds(
                id = 1,
                points = StrokeRealFixtures.points(50.0 to 60.0, 70.0 to 80.0),
                brush = brush,
                bounds = originalBounds
            )

            val cached = buildGeometry(stroke, Path())

            assertEquals("$type: bounds не должны расширяться", originalBounds, cached.bounds)
        }
    }

    // ---------- highlighter: alpha clamp ----------

    @Test
    fun `highlighter alphaClamped toMax whenOpacityIsOne`() {
        val brush = BrushFixtures.brush(BrushType.HIGHLIGHTER, opacity = 1.0f)
        val stroke = StrokeRealFixtures.withExplicitBounds(
            id = 1,
            points = StrokeRealFixtures.points(50.0 to 60.0, 70.0 to 80.0),
            brush = brush,
            bounds = bounds
        )
        val cached = buildGeometry(stroke, Path())
        assertEquals("opacity=1.0 → alpha=0.45", 0.45f, cached.color.alpha, EPS_ALPHA)
    }

    @Test
    fun `highlighter alphaClamped toMin whenOpacityIsLow`() {
        val brush = BrushFixtures.brush(BrushType.HIGHLIGHTER, opacity = 0.1f)
        val stroke = StrokeRealFixtures.withExplicitBounds(
            id = 1,
            points = StrokeRealFixtures.points(50.0 to 60.0, 70.0 to 80.0),
            brush = brush,
            bounds = bounds
        )
        val cached = buildGeometry(stroke, Path())
        assertEquals("opacity=0.1 → alpha=0.35", 0.35f, cached.color.alpha, EPS_ALPHA)
    }

    @Test
    fun `highlighter alphaNotClamped_inMiddleOfRange`() {
        val brush = BrushFixtures.brush(BrushType.HIGHLIGHTER, opacity = 0.4f)
        val stroke = StrokeRealFixtures.withExplicitBounds(
            id = 1,
            points = StrokeRealFixtures.points(50.0 to 60.0, 70.0 to 80.0),
            brush = brush,
            bounds = bounds
        )
        val cached = buildGeometry(stroke, Path())
        assertEquals(0.4f, cached.color.alpha, EPS)
    }

    @Test
    fun `nonHighlighter alphaMatchesBrushOpacity`() {
        val brush = BrushFixtures.brush(BrushType.BALLPOINT, opacity = 0.25f)
        val stroke = StrokeRealFixtures.created(1, 10.0 to 10.0, 90.0 to 90.0, brush = brush)

        val cached = buildGeometry(stroke, Path())

        assertEquals(0.25f, cached.color.alpha, EPS_ALPHA)
    }
    // ---------- цвет ----------

    @Test
    fun `color preservesArgb`() {
        val colorArgb = 0xFF1A2B3C.toInt()
        val brush = BrushFixtures.brush(BrushType.BALLPOINT, colorArgb = colorArgb)
        val stroke = StrokeRealFixtures.created(1, 10.0 to 10.0, 90.0 to 90.0, brush = brush)

        val cached = buildGeometry(stroke, Path())

        val expectedRgb = Color(colorArgb)
        assertEquals(expectedRgb.red,   cached.color.red,   1e-3f)
        assertEquals(expectedRgb.green, cached.color.green, 1e-3f)
        assertEquals(expectedRgb.blue,  cached.color.blue,  1e-3f)
    }

    // ---------- targetPath.reset ----------

    @Test
    fun `buildGeometry resets target path before building single dot`() {
        val brush = BrushFixtures.brush(BrushType.BALLPOINT)
        val stroke = StrokeRealFixtures.withExplicitBounds(
            id = 1,
            points = listOf(StrokePoint(50.0, 50.0, 0.5f)),
            brush = brush,
            bounds = bounds
        )
        val path = Path().apply {
            moveTo(999f, 999f)
            lineTo(1000f, 1000f)
        }
        assertFalse("path должен содержать мусор до вызова", path.isEmpty)

        buildGeometry(stroke, path)

        // ВАЖНО: не используем path.isEmpty — в Robolectric ShadowPath.isEmpty()
        // после reset() на ранее непустом Path возвращает false (артефакт теней).
        // getBounds() отражает реальное состояние и уже используется в соседнем
        // тесте `buildGeometry replaces path content for multi-point stroke`.
        val b = path.getBounds()
        assertTrue(
            "path должен быть очищен, bounds=(${b.left}, ${b.top}, ${b.right}, ${b.bottom})",
            b.left == 0f && b.top == 0f && b.right == 0f && b.bottom == 0f
        )
    }

    @Test
    fun `buildGeometry replaces path content for multi-point stroke`() {
        val brush = BrushFixtures.brush(BrushType.BALLPOINT)
        val stroke = StrokeRealFixtures.created(1, 10.0 to 10.0, 100.0 to 100.0, brush = brush)
        val path = Path().apply {
            moveTo(999f, 999f)
            lineTo(1000f, 1000f)
        }

        buildGeometry(stroke, path)

        val b = path.getBounds()
        // Новый path в диапазоне [10..100], не содержит старых 999/1000
        assertTrue("path должен быть пересобран, min=${b.left}", b.left >= 5f)
        assertTrue("path должен быть пересобран, max=${b.right}", b.right <= 105f)
    }

    // ---------- граничный n = 0 ----------

    @Test
    fun `emptyPoints does not throw`() {
        // Регрессионный тест текущего поведения. В UI guard: drawActiveStroke
        // вызывает buildGeometry только при activePointCount > 0.
        // TODO (техдолг): n=0 возвращает CachedStrokeGeometry с NaN в strokeWidth.
        val brush = BrushFixtures.brush(BrushType.BALLPOINT)
        val stroke = StrokeRealFixtures.withExplicitBounds(
            id = 1,
            points = emptyList(),
            brush = brush,
            bounds = bounds
        )

        // Не падает — контракт подтверждён. Содержимое для n=0 не валидируем.
        buildGeometry(stroke, Path())
    }

    private companion object {
        const val EPS = 1e-4f
        const val EPS_BOUNDS = 1e-3
        // Compose Color — 8-битный sRGB: alpha квантуется шагом 1/255,
        // поэтому прямое сравнение с исходным float ломается:
        //   0.25 → 0.2509804, 0.35 → 0.34901962, 0.45 → 0.4509804
        // 0.4 проходит только потому, что 0.4*255 == 102 — целое.
        const val EPS_ALPHA = 1f / 255f
    }
}