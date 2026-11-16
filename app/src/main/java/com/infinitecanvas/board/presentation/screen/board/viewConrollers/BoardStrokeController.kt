package com.infinitecanvas.board.presentation.screen.board.viewConrollers

import com.infinitecanvas.board.domain.model.Stroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class BoardStrokeController(
    scope: CoroutineScope,
    private val onContentChanged: () -> Unit,
    private val onSelectionActivated: () -> Unit
) {
    private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
    val strokes: StateFlow<List<Stroke>> = _strokes.asStateFlow()

    private val _selectedStrokeIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedStrokeIds: StateFlow<Set<Long>> = _selectedStrokeIds.asStateFlow()

    private val undoStack = ArrayDeque<List<Stroke>>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _clipboardStrokes = MutableStateFlow<List<Stroke>>(emptyList())
    val hasClipboardContent: StateFlow<Boolean> = _clipboardStrokes
        .map { it.isNotEmpty() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private var pasteCascadeCount = 0

    fun setInitialStrokes(strokes: List<Stroke>) {
        undoStack.clear()
        _canUndo.value = false
        _selectedStrokeIds.value = emptySet()
        _strokes.value = strokes
    }

    fun onStrokeCommitted(stroke: Stroke) {
        val currentList = _strokes.value
        pushUndoSnapshot(currentList)
        _strokes.value = currentList + stroke
        onContentChanged()
    }

    fun eraseStrokesByIds(idsToRemove: Set<Long>) {
        if (idsToRemove.isEmpty()) return
        val currentList = _strokes.value
        val filtered = currentList.filterNot { it.id in idsToRemove }
        if (filtered.size != currentList.size) {
            pushUndoSnapshot(currentList)
            _strokes.value = filtered
            onContentChanged()
        }
    }

    fun selectStrokes(ids: Set<Long>) {
        _selectedStrokeIds.value = ids
        if (ids.isNotEmpty()) {
            onSelectionActivated()
        }
    }

    fun clearSelection() {
        _selectedStrokeIds.value = emptySet()
    }

    fun deleteSelectedStrokes() {
        val ids = _selectedStrokeIds.value
        if (ids.isEmpty()) return
        val currentList = _strokes.value
        val filtered = currentList.filterNot { it.id in ids }
        if (filtered.size != currentList.size) {
            pushUndoSnapshot(currentList)
            _strokes.value = filtered
            onContentChanged()
        }
        _selectedStrokeIds.value = emptySet()
    }

    fun moveSelectedStrokes(dx: Double, dy: Double) {
        val ids = _selectedStrokeIds.value
        if (ids.isEmpty() || (dx == 0.0 && dy == 0.0)) return
        val currentList = _strokes.value
        pushUndoSnapshot(currentList)
        _strokes.value = currentList.map { stroke ->
            if (stroke.id in ids) stroke.translated(dx, dy) else stroke
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
        _strokes.value = currentList + pastedStrokes
        _selectedStrokeIds.value = pastedStrokes.mapTo(HashSet(pastedStrokes.size)) { it.id }
        onSelectionActivated()
        onContentChanged()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        _strokes.value = previous
        _canUndo.value = undoStack.isNotEmpty()
        _selectedStrokeIds.value = emptySet()
        onContentChanged()
    }

    private fun pushUndoSnapshot(snapshot: List<Stroke>) {
        if (undoStack.size >= 40) {
            undoStack.removeFirst()
        }
        undoStack.addLast(snapshot)
        _canUndo.value = true
    }
}