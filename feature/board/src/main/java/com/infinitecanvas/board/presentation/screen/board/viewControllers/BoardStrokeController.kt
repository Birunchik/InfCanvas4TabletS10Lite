package com.infinitecanvas.board.presentation.screen.board.viewControllers

import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.domain.spatial.Quadtree
import com.infinitecanvas.board.presentation.canvas.computeSelectionWorldBounds
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Штрихи — PersistentList: pushUndoSnapshot кладёт ссылку на старую версию, а не
// копию, поэтому undo дешёвый и не может испортить текущий список.
internal class BoardStrokeController(
    scope: CoroutineScope,
    private val onContentChanged: () -> Unit,
    private val onSelectionActivated: () -> Unit
) {
    private val _strokes = MutableStateFlow<PersistentList<Stroke>>(persistentListOf())
    val strokes: StateFlow<PersistentList<Stroke>> = _strokes.asStateFlow()

    private val _selectedStrokeIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedStrokeIds: StateFlow<Set<Long>> = _selectedStrokeIds.asStateFlow()

    private val _selectionBounds = MutableStateFlow<WorldBounds?>(null)
    val selectionBounds: StateFlow<WorldBounds?> = _selectionBounds.asStateFlow()

    // Мутабельный quadtree: insert/remove обновляют in-place, StateFlow не эмитит
    // на мутациях (тот же instance по ==). Эмитит только при замене instance
    // (undo, setInitialStrokes) — чтобы UI перерисовал с новым деревом.
    private val _quadtree = MutableStateFlow(Quadtree.build(emptyList()))
    val quadtree: StateFlow<Quadtree> = _quadtree.asStateFlow()

    private val undoStack = ArrayDeque<PersistentList<Stroke>>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _clipboardStrokes = MutableStateFlow<List<Stroke>>(emptyList())
    val hasClipboardContent: StateFlow<Boolean> = _clipboardStrokes
        .map { it.isNotEmpty() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private var pasteCascadeCount = 0

    // Инвариант: _selectionBounds = computeSelectionWorldBounds(_strokes, _selectedStrokeIds).
    // Единственная точка записи bounds из IDs; moveSelectedStrokes сознательно
    // не использует хелпер — там арифметика +dx/+dy дешевле полного пересчёта
    // и корректна, пока bounds согласованы (а они согласованы, потому что все
    // остальные мутаторы идут через этот хелпер).
    private fun recomputeSelectionBounds() {
        val ids = _selectedStrokeIds.value
        _selectionBounds.value = if (ids.isEmpty()) null
        else computeSelectionWorldBounds(_strokes.value, ids)
    }

    // История не переживает загрузку: undo вернул бы штрихи прошлой доски. Выделение
    // тоже сбрасывается — id из старого документа невалидны.
    fun setInitialStrokes(strokes: List<Stroke>) {
        undoStack.clear()
        _canUndo.value = false
        _selectedStrokeIds.value = emptySet()
        _selectionBounds.value = null
        val persistent = strokes.toPersistentList()
        _strokes.value = persistent
        _quadtree.value = Quadtree.build(persistent)
    }

    fun onStrokeCommitted(stroke: Stroke) {
        val currentList = _strokes.value
        pushUndoSnapshot(currentList)
        _strokes.value = currentList.adding(stroke)
        _quadtree.value.insert(stroke)  // in-place, без эмита
        onContentChanged()
    }

    fun eraseStrokesByIds(idsToRemove: Set<Long>) {
        if (idsToRemove.isEmpty()) return
        val currentList = _strokes.value
        val filtered = currentList.removingAll { it.id in idsToRemove }
        if (filtered.size == currentList.size) return

        pushUndoSnapshot(currentList)
        _strokes.value = filtered
        idsToRemove.forEach { _quadtree.value.remove(it) }

        val selected = _selectedStrokeIds.value
        if (selected.isNotEmpty() && selected.any { it in idsToRemove }) {
            _selectedStrokeIds.value = selected - idsToRemove
            recomputeSelectionBounds()
        }

        onContentChanged()
    }

    fun selectStrokes(ids: Set<Long>) {
        _selectedStrokeIds.value = ids
        recomputeSelectionBounds()

        if (ids.isNotEmpty()) {
            onSelectionActivated()
        }
    }

    fun clearSelection() {
        _selectedStrokeIds.value = emptySet()
        _selectionBounds.value = null
    }

    fun deleteSelectedStrokes() {
        val ids = _selectedStrokeIds.value
        if (ids.isEmpty()) return
        val currentList = _strokes.value
        val filtered = currentList.removingAll { it.id in ids }
        if (filtered.size != currentList.size) {
            pushUndoSnapshot(currentList)
            _strokes.value = filtered
            ids.forEach { _quadtree.value.remove(it) }
            onContentChanged()
        }
        _selectedStrokeIds.value = emptySet()
        _selectionBounds.value = null
    }

    // Сначала remove старых id, потом insert перемещённых: insert по существующему id
    // оставил бы в quadtree orphan-узел со старыми bounds.
    fun moveSelectedStrokes(dx: Double, dy: Double) {
        val ids = _selectedStrokeIds.value
        if (ids.isEmpty() || (dx == 0.0 && dy == 0.0)) return
        val currentList = _strokes.value
        pushUndoSnapshot(currentList)

        val movedStrokes = ArrayList<Stroke>(ids.size)
        _strokes.value = currentList
            .map { stroke ->
                if (stroke.id in ids) {
                    val moved = stroke.translated(dx, dy)
                    movedStrokes.add(moved)
                    moved
                } else stroke
            }
            .toPersistentList()

        ids.forEach { _quadtree.value.remove(it) }
        movedStrokes.forEach { _quadtree.value.insert(it) }

        // Арифметика вместо recomputeSelectionBounds(): полный пересчёт на каждом
        // drag-кадре — O(N), а сдвиг bounds на +dx/+dy — O(1). Корректно, потому
        // что bounds согласованы с выделением (инвариант выше).
        _selectionBounds.value?.let { b ->
            _selectionBounds.value = WorldBounds(
                minX = b.minX + dx,
                minY = b.minY + dy,
                maxX = b.maxX + dx,
                maxY = b.maxY + dy
            )
        }

        onContentChanged()
    }

    fun copySelectedStrokes() {
        val ids = _selectedStrokeIds.value
        if (ids.isEmpty()) return
        val copied = _strokes.value.filter { it.id in ids }
        if (copied.isNotEmpty()) {
            _clipboardStrokes.value = copied
            pasteCascadeCount = 0
        }
    }

    // id = nanoTime + index: нужна глобальная уникальность, иначе повторная вставка
    // дала бы дубликаты. pasteCascadeCount даёт каскадный сдвиг по 28 px.
    fun pasteClipboardStrokes() {
        val clipboard = _clipboardStrokes.value
        if (clipboard.isEmpty()) return

        pasteCascadeCount++
        val offsetStep = 28.0 * pasteCascadeCount
        val baseId = System.nanoTime()

        val pastedStrokes = clipboard.mapIndexed { index, stroke ->
            stroke
                .translated(offsetStep, offsetStep)
                .copy(id = baseId + index + 1L)
        }

        val currentList = _strokes.value
        pushUndoSnapshot(currentList)
        _strokes.value = currentList.addingAll(pastedStrokes)
        pastedStrokes.forEach { _quadtree.value.insert(it) }

        val newIds = pastedStrokes.mapTo(HashSet(pastedStrokes.size)) { it.id }
        _selectedStrokeIds.value = newIds
        recomputeSelectionBounds()
        onSelectionActivated()
        onContentChanged()
    }

    // Undo возвращает immutable-снапшот и пересобирает quadtree целиком: in-place
    // удаления не откатить. Выделение сбрасывается — этих id в снапшоте может не быть.
    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        _strokes.value = previous
        // Полная пересборка: O(N log N), ~2–5 ms для 10k штрихов.
        _quadtree.value = Quadtree.build(previous)
        _canUndo.value = undoStack.isNotEmpty()
        _selectedStrokeIds.value = emptySet()
        _selectionBounds.value = null
        onContentChanged()
    }

    // Лимит 40 ограничивает память, а не число шагов: снапшоты структурно делят узлы
    // с текущим списком, поэтому мелкая правка стоит дёшево.
    private fun pushUndoSnapshot(snapshot: PersistentList<Stroke>) {
        if (undoStack.size >= 40) {
            undoStack.removeFirst()
        }
        undoStack.addLast(snapshot)
        _canUndo.value = true
    }
}