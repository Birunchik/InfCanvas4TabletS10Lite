package com.infinitecanvas.board.presentation.canvas.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.presentation.canvas.StrokeGeometryCache

// Один экземпляр на Canvas: Path и буферы переживают кадр, поэтому наружу их отдавать нельзя.
/**
 * Держит все аллоцируемые на кадр объекты и состояние инвалидации,
 * которое Canvas читает, чтобы перерисоваться при мутации gesture handler.
 */
internal class CanvasRenderState {
    val pathCache = StrokeGeometryCache()
    val activeStrokeCache = ActiveStrokeCache()

    val activePath = Path()
    val minorGridPath = Path()
    val majorGridPath = Path()

    // Ёмкость с запасом: clear() не освобождает массив, поэтому после прогрева query не аллоцирует.
    val visibleStrokesBuffer = ArrayList<Stroke>(128)
    val draggedStrokesBuffer = ArrayList<Stroke>(64)

    // Переиспользуемые буферы для hit-test ластика — ноль аллокаций на движение.
    val eraserCandidatesBuffer = ArrayList<Stroke>(16)
    val eraserHitIdsBuffer = HashSet<Long>()

    // Координаты углов рамки выделенной картинки для OverlayRenderer: listOf(Offset...)
    // на кадр аллоцировал список и 4 Offset'а. FloatArray выровнен по [x0,y0,x1,y1,...]
    // и переживает кадр, поэтому draw-проход пишет в него, а не создаёт объекты.
    val imageHandleCorners = FloatArray(8)

    // Отпечаток последнего отсортированного visibleStrokesBuffer: draw-фаза знает
    // состав штрихов в кадре, и при неизменном составе сортировать заново не нужно.
    // null — буфер ещё не сортировался (следующий кадр обязан отсортировать).
    private var lastSortStamp: SortStamp? = null

    /**
     * Сортирует [visibleStrokesBuffer] по id, только если состав набора изменился.
     *
     * Порядок обхода quadtree зависит от раскладки дерева, а не от времени рисования,
     * поэтому без сортировки штрихи меняли бы порядок наложения между кадрами.
     * Сортировка по id восстанавливает порядок создания. Вызывается из draw-фазы,
     * поэтому при повторном кадре с тем же набором (панорамирование без правок,
     * перерисовка из-за invalidate) сортировка пропускается целиком — это и убирает
     * аллокацию списка, которую создавал sortBy.
     *
     * Отпечаток — размер + хэши id в порядке появления. Он не ловит редкий случай
     * «другой набор с тем же хэшем», но там нарушится только порядок наложения,
     * а не состав: для устойчивости этого достаточно, а полная проверка вернула бы O(n).
     */
    fun sortVisibleStrokesIfChanged() {
        val buffer = visibleStrokesBuffer
        val stamp = SortStamp.of(buffer)
        // Штрихи уже отсортированы (обход дерева часто совпадает с порядком вставки),
        // и состав тот же — второй проход не нужен.
        if (stamp == lastSortStamp && isSortedById(buffer)) return
        buffer.sortBy { it.id }
        lastSortStamp = stamp
    }

    private fun isSortedById(buffer: List<Stroke>): Boolean {
        for (i in 1 until buffer.size) {
            if (buffer[i - 1].id > buffer[i].id) return false
        }
        return true
    }

    val screenDashEffect: PathEffect =
        PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)

    private var dashScale: Float = -1f
    private var worldDashCache: PathEffect? = null

    /** Ленивый кэш dash-эффекта для world-space лассо. Аллокация только при смене масштаба. */
    fun worldDashEffect(currentScale: Float): PathEffect {
        val cached = worldDashCache
        if (cached != null && dashScale == currentScale) return cached
        dashScale = currentScale
        // Делим длины на масштаб: PathEffect задаётся в world-единицах, и без этого пунктир
        // на экране растягивался бы вместе с зумом.
        return PathEffect.dashPathEffect(computeWorldDashIntervals(currentScale), 0f)
            .also { worldDashCache = it }
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
internal fun rememberCanvasRenderState(): CanvasRenderState =
    remember { CanvasRenderState() }
internal fun computeWorldDashIntervals(scale: Float): FloatArray =
    floatArrayOf(14f / scale, 10f / scale)

/**
 * Дешёвый отпечаток набора штрихов в кадре: count + сумма хэшей id.
 * Не является идентификатором (коллизии возможны), но для решения
 * «изменился ли состав набора» этого достаточно и стоит O(n) без аллокаций.
 */
internal class SortStamp private constructor(
    private val count: Int,
    private val hash: Long
) {
    override fun equals(other: Any?): Boolean =
        other is SortStamp && other.count == count && other.hash == hash

    override fun hashCode(): Int = 31 * count + (hash xor (hash ushr 32)).toInt()

    companion object {
        fun of(strokes: List<Stroke>): SortStamp {
            var h = 1469598103934665603L
            val prime = 1099511628211L
            for (i in strokes.indices) {
                // Смешиваем id позиционно, чтобы перестановка меняла отпечаток.
                h = (h xor (strokes[i].id * prime)) * prime
            }
            return SortStamp(strokes.size, h)
        }
    }
}
