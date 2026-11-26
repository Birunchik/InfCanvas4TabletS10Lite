package com.infinitecanvas.board.presentation.canvas

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.CanvasTool
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.domain.spatial.Quadtree
import com.infinitecanvas.board.testing.BrushFixtures
import com.infinitecanvas.board.testing.FakeImageStore
import com.infinitecanvas.board.testing.StrokeRealFixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Интеграционные тесты жестов CanvasGestureHandler через Compose UI Test.
 *
 * ВАЖНО про стилус: Compose UI Test 1.13 не предоставляет performStylusInput —
 * доступны только performTouchInput / performMouseInput / performKeyInput /
 * performScrollWheelInput. Стилус (PointerType.Stylus) через публичный API
 * недостижим. Тесты эрейзера и S-Pen-специфики перенесены в Plan 2 Phase 5
 * (StylusHardwareButtonTest) через raw MotionEvent с toolType=TOOL_TYPE_STYLUS.
 *
 * Покрываемые сценарии:
 *   - pan (1 палец без finger drawing) → onTransform с pan
 *   - pinch (2 пальца) → onTransform с zoom
 *   - pinch при isZoomLocked → zoom=1
 *   - touch drawing с allowFingerDrawing → onStrokeCommitted (тот же путь,
 *     что стилус, кроме проверки PointerType)
 *   - touch tap без allowFingerDrawing → НЕ onStrokeCommitted (только pan)
 *   - touch + LASSO вокруг штриха → onStrokesSelected с id
 *   - lasso при непустом selected → очистка через onStrokesSelected(emptySet())
 *   - touch tap по unpinned image → onImageSelected(id)
 *   - touch drag выделенной image → onUpdateImageBounds ровно 1 раз (на up)
 */
@RunWith(AndroidJUnit4::class)
class CanvasGestureIntegrationTest {

    @get:Rule
    val compose = createComposeRule()

    private val imageStore = FakeImageStore()
    private lateinit var imageCacheScope: CoroutineScope
    private lateinit var imageCache: BoardImageCache
    private val viewport = ViewportState()

    @Before
    fun setUp() {
        imageCacheScope = CoroutineScope(Dispatchers.Main)
        imageCache = BoardImageCache(
            imageStore = imageStore,
            scope = imageCacheScope,
            onImageLoaded = {},
        )
    }

    @After
    fun tearDown() {
        imageCacheScope.cancel()
    }

    @Composable
    private fun CanvasUnderTest(
        quadtree: Quadtree = Quadtree.build(emptyList()),
        images: List<BoardImage> = emptyList(),
        selectedImageId: Long? = null,
        selectedStrokeIds: Set<Long> = emptySet(),
        activeTool: CanvasTool = CanvasTool.BRUSH,
        activeBrush: Brush = Brush(),
        isZoomLocked: Boolean = false,
        allowFingerDrawing: Boolean = false,
        onStrokeCommitted: (Stroke) -> Unit = {},
        onEraseStrokes: (Set<Long>) -> Unit = {},
        onStrokesSelected: (Set<Long>) -> Unit = {},
        onImageSelected: (Long?) -> Unit = {},
        onUpdateImageBounds: (Long, WorldBounds) -> Unit = { _, _ -> },
        onTransform: (Float, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    ) {
        InfiniteCanvasView(
            quadtree = quadtree,
            boardId = 1L,
            viewportState = viewport,
            images = images,
            selectedImageId = selectedImageId,
            imageCache = imageCache,
            activeTool = activeTool,
            activeBrush = activeBrush,
            selectedStrokeIds = selectedStrokeIds,
            selectionBounds = null,
            isZoomLocked = isZoomLocked,
            allowFingerDrawing = allowFingerDrawing,
            onCanvasViewportSizeChanged = { _, _ -> },
            onStrokeCommitted = onStrokeCommitted,
            onEraseStrokes = onEraseStrokes,
            onStrokesSelected = onStrokesSelected,
            onMoveSelectedStrokes = { _, _ -> },
            onImageSelected = onImageSelected,
            onUpdateImageBounds = onUpdateImageBounds,
            onTransform = onTransform,
            modifier = Modifier.size(400.dp).testTag("canvas"),
        )
    }

    /** v2-правило может вернуть управление до появления Compose-дерева на холодном старте. */
    private fun awaitCanvas() {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithTag("canvas").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ---------- pan / zoom ----------

    @Test
    fun panGesture_emitsTransform() {
        val transforms = mutableListOf<Transform>()
        compose.setContent {
            CanvasUnderTest(onTransform = { px, py, z, _, _ ->
                transforms += Transform(px, py, z)
            })
        }
        compose.waitForIdle()
        awaitCanvas()

        compose.onNodeWithTag("canvas").performTouchInput {
            swipe(start = Offset(100f, 200f), end = Offset(300f, 200f), durationMillis = 200)
        }
        compose.waitForIdle()
        awaitCanvas()

        assertTrue("onTransform должен был сработать, events=${transforms.size}", transforms.isNotEmpty())
        assertTrue(
            "сумма panX должна быть > 0 (свайп вправо)",
            transforms.sumOf { it.panX.toDouble() } > 0.0
        )
    }

    @Test
    fun pinchGesture_emitsZoom() {
        val zooms = mutableListOf<Float>()
        compose.setContent {
            CanvasUnderTest(onTransform = { _, _, z, _, _ -> zooms += z })
        }
        compose.waitForIdle()
        awaitCanvas()

        compose.onNodeWithTag("canvas").performTouchInput {
            val cx = centerX
            val cy = centerY
            pinch(
                start0 = Offset(cx - 50f, cy),
                end0 = Offset(cx - 150f, cy),
                start1 = Offset(cx + 50f, cy),
                end1 = Offset(cx + 150f, cy)
            )
        }
        compose.waitForIdle()
        awaitCanvas()

        assertTrue("pinch должен дать zoom > 1, получено: $zooms", zooms.any { it > 1f })
    }

    @Test
    fun pinchGesture_zoomLocked_emitsZoom1() {
        val zooms = mutableListOf<Float>()
        compose.setContent {
            CanvasUnderTest(
                isZoomLocked = true,
                onTransform = { _, _, z, _, _ -> zooms += z }
            )
        }
        compose.waitForIdle()
        awaitCanvas()

        compose.onNodeWithTag("canvas").performTouchInput {
            pinch(
                start0 = Offset(centerX - 50f, centerY),
                end0 = Offset(centerX - 150f, centerY),
                start1 = Offset(centerX + 50f, centerY),
                end1 = Offset(centerX + 150f, centerY)
            )
        }
        compose.waitForIdle()
        awaitCanvas()

        assertTrue("zoomLocked → все zoom == 1f, получено: $zooms", zooms.all { it == 1f })
    }

    // ---------- drawing (touch эквивалент stylus-пути) ----------

    @Test
    fun touchDrawing_commitsStroke() {
        // allowFingerDrawing=true → touch идёт в handleStrokeOrEraserGesture — тот же
        // путь, что у стилуса (isSingleFingerAllowed открывает ту же ветку).
        // Стилус-специфика (PointerType, pressure, кнопки) — Phase 5.
        val strokes = mutableListOf<Stroke>()
        compose.setContent {
            CanvasUnderTest(
                activeTool = CanvasTool.BRUSH,
                allowFingerDrawing = true,
                onStrokeCommitted = { strokes += it }
            )
        }
        compose.waitForIdle()
        awaitCanvas()

        compose.onNodeWithTag("canvas").performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(150f, 150f))
            moveTo(Offset(200f, 200f))
            moveTo(Offset(250f, 250f))
            up()
        }
        compose.waitForIdle()
        awaitCanvas()

        assertEquals("1 commit на up", 1, strokes.size)
        assertTrue("committed stroke должен иметь >= 2 точек", strokes[0].points.size >= 2)
    }

    @Test
    fun fingerDrawing_disabled_doesNotDraw() {
        val strokes = mutableListOf<Stroke>()
        compose.setContent {
            CanvasUnderTest(
                activeTool = CanvasTool.BRUSH,
                allowFingerDrawing = false,
                onStrokeCommitted = { strokes += it }
            )
        }
        compose.waitForIdle()
        awaitCanvas()

        compose.onNodeWithTag("canvas").performTouchInput {
            swipe(start = Offset(100f, 100f), end = Offset(300f, 300f))
        }
        compose.waitForIdle()
        awaitCanvas()

        assertTrue(
            "без allowFingerDrawing touch идёт в pan, не в рисование",
            strokes.isEmpty()
        )
    }

    @Test
    fun fingerDrawing_enabled_draws() {
        val strokes = mutableListOf<Stroke>()
        compose.setContent {
            CanvasUnderTest(
                activeTool = CanvasTool.BRUSH,
                allowFingerDrawing = true,
                onStrokeCommitted = { strokes += it }
            )
        }
        compose.waitForIdle()
        awaitCanvas()

        compose.onNodeWithTag("canvas").performTouchInput {
            swipe(start = Offset(100f, 100f), end = Offset(300f, 300f), durationMillis = 200)
        }
        compose.waitForIdle()
        awaitCanvas()

        assertEquals("allowFingerDrawing=true → 1 stroke", 1, strokes.size)
    }

    // ---------- lasso ----------

    @Test
    fun lasso_selectsStrokes() {
        // Штрих в (100..150). Lasso-прямоугольник (50,50)-(250,250) полностью его покрывает.
        val targetStroke = StrokeRealFixtures.created(
            id = 7L, 100.0 to 100.0, 150.0 to 150.0,
            brush = BrushFixtures.brush(BrushType.BALLPOINT, baseSize = 10f)
        )
        val quadtree = Quadtree.build(listOf(targetStroke))

        val selections = mutableListOf<Set<Long>>()
        compose.setContent {
            CanvasUnderTest(
                quadtree = quadtree,
                activeTool = CanvasTool.LASSO,
                allowFingerDrawing = true,
                onStrokesSelected = { selections += it }
            )
        }
        compose.waitForIdle()
        awaitCanvas()

        compose.onNodeWithTag("canvas").performTouchInput {
            down(Offset(50f, 50f))
            moveTo(Offset(250f, 50f))
            moveTo(Offset(250f, 250f))
            moveTo(Offset(50f, 250f))
            moveTo(Offset(50f, 50f))
            up()
        }
        compose.waitForIdle()
        awaitCanvas()

        assertTrue("LassoSelector должен был найти id=7", selections.any { 7L in it })
    }

    @Test
    fun lasso_onStart_clearsSelection() {
        // Начинаем с непустым выделением. Первый же onStrokesSelected должен быть emptySet().
        val selections = mutableListOf<Set<Long>>()
        compose.setContent {
            CanvasUnderTest(
                activeTool = CanvasTool.LASSO,
                allowFingerDrawing = true,
                selectedStrokeIds = setOf(99L),
                onStrokesSelected = { selections += it }
            )
        }
        compose.waitForIdle()
        awaitCanvas()

        compose.onNodeWithTag("canvas").performTouchInput {
            down(Offset(50f, 50f))
            moveTo(Offset(100f, 50f))
            up()
        }
        compose.waitForIdle()
        awaitCanvas()

        assertTrue("должна быть очистка при старте", selections.isNotEmpty())
        assertEquals(
            "первый вызов onStrokesSelected — очистка",
            emptySet<Long>(),
            selections.first()
        )
    }

    // ---------- image ----------

    @Test
    fun imageTap_onUnpinned_selectsImage() {
        // Unpinned image (world): bounds = (50..150). Tap в (100,100) → world hit.
        val img = BoardImage(
            id = 1L,
            fileName = "test.png",
            bounds = WorldBounds(50.0, 50.0, 150.0, 150.0),
            isPinned = false
        )
        val selectedIds = mutableListOf<Long?>()
        compose.setContent {
            CanvasUnderTest(
                images = listOf(img),
                allowFingerDrawing = false,
                onImageSelected = { selectedIds += it }
            )
        }
        compose.waitForIdle()
        awaitCanvas()

        compose.onNodeWithTag("canvas").performTouchInput {
            click(Offset(100f, 100f))
        }
        compose.waitForIdle()
        awaitCanvas()

        assertTrue("onImageSelected должен содержать id=1", selectedIds.any { it == 1L })
    }

    @Test
    fun imageDrag_commitsOnlyOnRelease() {
        // Выделенный unpinned image, drag из (100,100) в (150,150).
        // onUpdateImageBounds — ровно 1 раз, на up, с новым bounds (world сдвинут на +50,+50).
        val img = BoardImage(
            id = 1L,
            fileName = "test.png",
            bounds = WorldBounds(50.0, 50.0, 150.0, 150.0),
            isPinned = false
        )
        val updates = mutableListOf<Pair<Long, WorldBounds>>()
        compose.setContent {
            CanvasUnderTest(
                images = listOf(img),
                selectedImageId = 1L,
                onUpdateImageBounds = { id, b -> updates += id to b }
            )
        }
        compose.waitForIdle()
        awaitCanvas()

        compose.onNodeWithTag("canvas").performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(125f, 125f))
            moveTo(Offset(150f, 150f))
            up()
        }
        compose.waitForIdle()
        awaitCanvas()

        assertEquals("ровно 1 commit на up", 1, updates.size)
        val (id, newBounds) = updates[0]
        assertEquals(1L, id)
        // Сдвиг ~ +50,+50 от исходного (50..150): drag от 100→150 = 50px world
        assertEquals(100.0, newBounds.minX, 5.0)
        assertEquals(100.0, newBounds.minY, 5.0)
        assertEquals(200.0, newBounds.maxX, 5.0)
        assertEquals(200.0, newBounds.maxY, 5.0)
    }

    // ---------- helpers ----------

    private data class Transform(val panX: Float, val panY: Float, val zoom: Float)
}