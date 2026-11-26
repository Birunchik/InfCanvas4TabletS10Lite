package com.infinitecanvas.board.presentation.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.domain.spatial.Quadtree
import com.infinitecanvas.board.testing.FakeImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke-тесты InfiniteCanvasView (Фаза 1 Plan 2).
 *
 * Цель — установить рабочий каркас Compose UI Test на реальном девайсе/эмуляторе,
 * проверить базовую композицию, размеры, отрисовку фона и безопасность рекомпозиции.
 *
 * Стилус-жесты, drawing и image manipulation — Plan 2 Фаза 2 (CanvasGestureIntegrationTest).
 */
@RunWith(AndroidJUnit4::class)
class InfiniteCanvasViewTest {

    @get:Rule
    val compose = createComposeRule()

    private val imageStore = FakeImageStore()
    private lateinit var imageCacheScope: CoroutineScope
    private lateinit var imageCache: BoardImageCache
    private lateinit var quadtree: Quadtree
    private val viewport = ViewportState()

    @Before
    fun setUp() {
        imageCacheScope = CoroutineScope(Dispatchers.Main)
        imageCache = BoardImageCache(
            imageStore = imageStore,
            scope = imageCacheScope,
            onImageLoaded = {},
        )
        quadtree = Quadtree.build(emptyList())
    }

    @After
    fun tearDown() {
        imageCacheScope.cancel()
    }

    /**
     * Полный набор параметров с возможностью переопределить любой.
     * Позволяет не дублировать 20 аргументов в каждом тесте.
     */
    @Composable
    private fun CanvasUnderTest(
        modifier: Modifier = Modifier,
        backgroundColor: Color = Color(0xFFF8F9FA),
        activeTool: CanvasTool = CanvasTool.BRUSH,
        activeBrush: Brush = Brush(),
        images: List<BoardImage> = emptyList(),
        onCanvasViewportSizeChanged: (Int, Int) -> Unit = { _, _ -> },
        onStrokeCommitted: (Stroke) -> Unit = {},
        onEraseStrokes: (Set<Long>) -> Unit = {},
        onTransform: (Float, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    ) {
        InfiniteCanvasView(
            quadtree = quadtree,
            boardId = 1L,
            viewportState = viewport,
            images = images,
            selectedImageId = null,
            imageCache = imageCache,
            activeTool = activeTool,
            activeBrush = activeBrush,
            selectedStrokeIds = emptySet(),
            selectionBounds = null,
            onCanvasViewportSizeChanged = onCanvasViewportSizeChanged,
            onStrokeCommitted = onStrokeCommitted,
            onEraseStrokes = onEraseStrokes,
            onStrokesSelected = {},
            onMoveSelectedStrokes = { _, _ -> },
            onImageSelected = {},
            onUpdateImageBounds = { _, _ -> },
            onTransform = onTransform,
            modifier = modifier,
            backgroundColor = backgroundColor
        )
    }

    // ---------- 1. Базовая композиция ----------

    @Test
    fun canvas_composes_withoutCrash() {
        compose.setContent { CanvasUnderTest() }
        compose.waitForIdle()

        // Если дошли сюда и root существует — композиция прошла успешно,
        // все зависимости (storage, cache, quadtree, viewport) валидны.
        compose.onRoot().assertExists()
    }

    // ---------- 2. onSizeChanged при измерении ----------

    @Test
    fun canvas_callsOnSizeChanged_whenMeasured() {
        val sizes = mutableListOf<Pair<Int, Int>>()
        compose.setContent {
            CanvasUnderTest(
                onCanvasViewportSizeChanged = { w, h -> sizes += w to h }
            )
        }
        compose.waitForIdle()

        assertTrue(
            "ожидался хотя бы один вызов onSizeChanged, получено: $sizes",
            sizes.isNotEmpty()
        )
        val (w, h) = sizes.last()
        assertTrue("width должен быть > 0, получено $w", w > 0)
        assertTrue("height должен быть > 0, получено $h", h > 0)
    }

    // ---------- 3. Отрисовка кастомного фона ----------

    @Test
    fun canvas_renders_customBackgroundColor() {
        compose.setContent {
            CanvasUnderTest(
                modifier = Modifier.testTag("canvas"),
                backgroundColor = Color.Red
            )
        }
        compose.waitForIdle()

        val bitmap = compose.onNodeWithTag("canvas").captureToImage().toPixelMap()

        // Берём несколько точек, далёких от линий сетки:
        // grid step = 50 world px при scale=1 → линии сетки на screen x/y ∈ {0, 50, 100, ...}.
        // (10, 10), (10, 30), (30, 10), (30, 30) — гарантированно вне линий.
        // Ждём хотя бы один чистый красный пиксель фона (фон есть, штрихов нет).
        val samples = listOf(
            bitmap[10, 10],
            bitmap[10, 30],
            bitmap[30, 10],
            bitmap[30, 30]
        )
        val hasRedBackground = samples.any { px ->
            (px.red > 0.8f) && (px.green < 0.2f) && (px.blue < 0.2f)
        }
        assertTrue(
            "хотя бы один из пикселей должен быть красным фоном, получено: $samples",
            hasRedBackground
        )
    }

    // ---------- 4. Рекомпозиция с изменёнными пропсами ----------

    @Test
    fun canvas_recomposes_withDifferentProps_withoutCrash() {
        // Типичный сценарий: пользователь меняет инструмент, кисть и перетаскивает картинку.
        // Проверяем, что remember-инфраструктура (gestureHandler, renderState) устойчива
        // к рекомпозиции — не падает и сохраняет работоспособное состояние.
        val tool = mutableStateOf(CanvasTool.BRUSH)
        val brush = mutableStateOf(Brush(type = BrushType.BALLPOINT))
        val images = mutableStateOf<List<BoardImage>>(emptyList())

        compose.setContent {
            CanvasUnderTest(
                activeTool = tool.value,
                activeBrush = brush.value,
                images = images.value
            )
        }
        compose.waitForIdle()

        // Меняем все три пропса одновременно
        tool.value = CanvasTool.ERASER
        brush.value = Brush(type = BrushType.HIGHLIGHTER, baseSize = 20f)
        images.value = listOf(
            BoardImage(
                id = 1L,
                fileName = "test_nonexistent.png",
                bounds = WorldBounds(0.0, 0.0, 100.0, 100.0),
                isPinned = false
            )
        )
        compose.waitForIdle()

        // Композиция не упала, root на месте.
        // Если бы rememberCanvasGestureHandler создавался с ключами по activeTool/brush/images,
        // он бы пересоздавался на каждое изменение — с новым activePoints/lassoPoints списками,
        // что в UI дало бы «потерю прогресса» (в стилус-сценариях).
        // Здесь smoke-уровень: рекомпозиция безопасна.
        compose.onRoot().assertExists()
    }
}