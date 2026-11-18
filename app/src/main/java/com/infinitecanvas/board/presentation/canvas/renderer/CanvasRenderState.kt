package com.infinitecanvas.board.presentation.canvas.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.presentation.canvas.StrokeGeometryCache

/**
 * Держит все аллоцируемые на кадр объекты и состояние инвалидации,
 * которое Canvas читает, чтобы перерисоваться при мутации gesture handler.
 */
class CanvasRenderState {
    val pathCache = StrokeGeometryCache()
    val activeStrokeCache = ActiveStrokeCache()

    val activePath = Path()
    val minorGridPath = Path()
    val majorGridPath = Path()

    val visibleStrokesBuffer = ArrayList<Stroke>(128)
    val draggedStrokesBuffer = ArrayList<Stroke>(64)

    // Переиспользуемые буферы для hit-test ластика — ноль аллокаций на движение.
    val eraserCandidatesBuffer = ArrayList<Stroke>(16)
    val eraserHitIdsBuffer = HashSet<Long>()

    val screenDashEffect: PathEffect =
        PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)

    private var dashScale: Float = -1f
    private var worldDashCache: PathEffect? = null

    /** Ленивый кэш dash-эффекта для world-space лассо. Аллокация только при смене масштаба. */
    fun worldDashEffect(currentScale: Float): PathEffect {
        val cached = worldDashCache
        if (cached != null && dashScale == currentScale) return cached
        dashScale = currentScale
        return PathEffect.dashPathEffect(
            floatArrayOf(14f / currentScale, 10f / currentScale),
            0f
        ).also { worldDashCache = it }
    }

    // Счётчик, читаемый внутри Canvas для принудительной перерисовки
    // при мутации gesture handler.
    private val _activeTick = mutableIntStateOf(0)
    val activeTick: Int get() = _activeTick.intValue

    fun invalidate() {
        _activeTick.intValue++
    }
}

@Composable
fun rememberCanvasRenderState(): CanvasRenderState =
    remember { CanvasRenderState() }