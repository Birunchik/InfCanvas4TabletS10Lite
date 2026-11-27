package com.infinitecanvas.board.presentation.screen.board

import android.annotation.SuppressLint
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitecanvas.board.domain.model.BrushType
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.domain.port.BoardThumbnailer
import com.infinitecanvas.board.domain.port.IdGenerator
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.domain.repository.BoardRepository
import com.infinitecanvas.board.presentation.screen.board.viewControllers.BoardImageController
import com.infinitecanvas.board.presentation.screen.board.viewControllers.BoardStrokeController
import com.infinitecanvas.board.presentation.screen.board.viewControllers.BoardToolController
import com.infinitecanvas.board.presentation.screen.board.viewControllers.BoardViewportController
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

// Дебаунс автосохранения: пауза тишины после последней правки.
private val AUTO_SAVE_DEBOUNCE = 1_500L.milliseconds

// Контроллеры — единственный источник состояния; ViewModel не держит копий
// штрихов/картинок и лишь связывает их колбэками, поэтому flush читает их снапшоты.
internal class BoardViewModel(
    private val repository: BoardRepository,
    private val thumbnailGenerator: BoardThumbnailer,
    imageStore: ImageStore,
    // Единственный источник id для штрихов/картинок. Сидируется при загрузке доски,
    // чтобы новые id шли после сохранённых (уникальность в пределах доски).
    private val idGenerator: IdGenerator,
    // Scope уровня приложения: переживает ViewModel, поэтому финальная запись
    // в onCleared может реально выполниться (viewModelScope к этому моменту отменён).
    private val applicationScope: CoroutineScope,
    // Дефолт Main.immediate сохраняет прежнее поведение: viewModelScope и так
    // работает на Main. Тесты передают StandardTestDispatcher, чтобы управлять
    // delay/дебаунсом виртуальным временем.
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {

    private var currentBoardId: Long? = null
    // Один активный таймер на все правки: scheduleAutoSave отменяет предыдущий,
    // то есть это дебаунс, а не очередь последовательных записей.
    // Отменяется только фаза delay — сама запись идёт в NonCancellable.
    private var saveTimerJob: Job? = null
    // Запрос «перегенерировать превью» переживает схлопывание нескольких правок в один таймер.
    private var pendingThumbnailRequest: Boolean = false
    // Сериализует записи: таймер может сработать, пока предыдущая запись ещё идёт.
    private val saveMutex = Mutex()
    private var hasUnsavedChanges: Boolean = false

    // Guard от повторного openBoard той же доски: пока идёт загрузка,
    // currentBoardId ещё не выставлен, и без этого флага LaunchedEffect(boardId)
    // мог бы запустить загрузку дважды. Это не UI-флаг — подписчиков нет.
    private val _isBoardLoadInProgress = MutableStateFlow(true)

    // Флаг «выход уже начат»: защищает от двойного flushSaveAndThumbnail
    // (повторный back, back + кнопка в топбаре). Вызовы идут только с main-потока
    // (BackHandler, onClick, onCleared), поэтому обычный var достаточен —
    // check-and-set не пересекается между потоками. После back экран и VM
    // уничтожаются, сбрасывать флаг не нужно.
    private var isExiting: Boolean = false

    private val viewportController = BoardViewportController(
        onViewportChanged = { scheduleAutoSave(regenerateThumbnail = false) }
    )

    private val strokeController: BoardStrokeController = BoardStrokeController(
        scope = viewModelScope,
        idGenerator = idGenerator,
        onContentChanged = { scheduleAutoSave(regenerateThumbnail = true) },
        onSelectionActivated = { imageController.clearSelection() }
    )

    private val imageController: BoardImageController = BoardImageController(
        imageStore = imageStore,
        scope = viewModelScope,
        idGenerator = idGenerator,
        viewportController = viewportController,
        onContentChanged = { scheduleAutoSave(regenerateThumbnail = true) },
        onSelectionActivated = { strokeController.clearSelection() }
    )

    private val toolController = BoardToolController(
        onClearSelectionRequested = {
            strokeController.clearSelection()
            imageController.clearSelection()
        }
    )

    // --- Проксируем StateFlow для UI без изменения публичного API ---
    val viewportState = viewportController.viewportState
    val isZoomLocked = viewportController.isZoomLocked

    // Только масштаб — для BoardScreen (перерисовка только при zoom, не при pan).
    val zoomScale: StateFlow<Double> = viewportState
        .map { it.scale }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)

    val strokes = strokeController.strokes
    val selectedStrokeIds = strokeController.selectedStrokeIds
    val selectionBounds = strokeController.selectionBounds
    val quadtree = strokeController.quadtree
    val canUndo = strokeController.canUndo
    val hasClipboardContent = strokeController.hasClipboardContent

    val images = imageController.images
    val selectedImageId = imageController.selectedImageId
    val hasSelectedImage = imageController.hasSelectedImage

    val isSelectedImagePinned = imageController.isSelectedImagePinned

    val activeTool = toolController.activeTool
    val activeBrush = toolController.activeBrush
    val presetColors = toolController.presetColors
    val selectedPresetIndex = toolController.selectedPresetIndex

    // --- Жизненный цикл и сохранение доски ---
    // Повторное открытие уже загруженной доски — no-op: setInitial* сбросил бы
    // undo-стек и выделение, хотя пользователь просто вернулся на тот же boardId.
    fun openBoard(boardId: Long) {
        if (currentBoardId == boardId && !_isBoardLoadInProgress.value) return
        viewModelScope.launch(dispatcher) {
            _isBoardLoadInProgress.value = true
            val doc = repository.loadBoard(boardId)
            if (doc != null) {
                currentBoardId = boardId
                // Сидим генератор id после загрузки: в файле уже лежат id штрихов
                // и картинок, и новые обязаны идти после них. Делаем это ДО setInitial*,
                // чтобы ни один новый объект не успел получить занятый id.
                // seedAtLeast монотонен, поэтому повторное открытие доски безопасно.
                idGenerator.seedAtLeast(
                    maxOf(
                        doc.strokes.maxOfOrNull { it.id } ?: 0L,
                        doc.images.maxOfOrNull { it.id } ?: 0L
                    )
                )
                strokeController.setInitialStrokes(doc.strokes)
                imageController.setInitialImages(doc.images)
                viewportController.setViewport(doc.viewport)
                hasUnsavedChanges = false
            }
            _isBoardLoadInProgress.value = false
        }
    }

    // Снапшоты снимает persist() в момент записи; flush лишь гарантирует, что
    // запись не будет отменена и не наложится на автосейв.
    fun flushSaveAndThumbnail(onFinished: (() -> Unit)? = null) {
        val boardId = currentBoardId ?: run {
            onFinished?.invoke()
            return
        }
        // Повторный back: сохранять второй раз не нужно — первый flush уже
        // пишет в NonCancellable и допишет сам. Навигацию завершаем сразу,
        // чтобы повторный back не висел в ожидании генерации миниатюры.
        if (isExiting) {
            onFinished?.invoke()
            return
        }
        isExiting = true

        // Отменяем отложенный автосейв: иначе после flush сработает ещё одна запись
        // той же доски, и порядок «кто последний» станет неопределённым.
        // Отмена безопасна: если автосейв уже пишет, его тело в NonCancellable,
        // а общий saveMutex не даст записям наложиться.
        saveTimerJob?.cancel()

        viewModelScope.launch(dispatcher) {
            // NonCancellable: отмена scope (onCleared) не должна оборвать запись на середине —
            // файл и строка Room пишутся без общей транзакции, и половина хуже отсутствия.
            withContext(NonCancellable) {
                // saveMutex: flush не должен наложиться на идущий автосейв.
                saveMutex.withLock {
                    persist(boardId = boardId, regenerateThumbnail = true)
                }
            }
            onFinished?.invoke()
        }
    }

    // Единственная точка записи доски. Снимки снимаются здесь, поэтому вызывающий
    // может менять состояние параллельно. Вызывается под saveMutex: две записи
    // не должны накладываться на один файл.
    private suspend fun persist(boardId: Long, regenerateThumbnail: Boolean) {
        val strokesSnapshot = strokes.value
        val imagesSnapshot = images.value
        val viewportSnapshot = viewportState.value
        val thumbPath = if (regenerateThumbnail) {
            thumbnailGenerator.generateAndSave(
                boardId = boardId,
                strokes = strokesSnapshot,
                images = imagesSnapshot
            )
        } else {
            null
        }
        repository.saveBoardContent(
            boardId = boardId,
            strokes = strokesSnapshot,
            images = imagesSnapshot,
            viewport = viewportSnapshot,
            thumbnailPath = thumbPath
        )
        hasUnsavedChanges = false
    }

    // Дебаунс 1.5 с: таймер перезапускается на каждое изменение. Миниатюра — только
    // при контентных правках: пан/зум меняют лишь viewport и её не требуют.
    //
    // Запись обёрнута в NonCancellable и сериализована мьютексом. Причина: пан/зум
    // вызывает scheduleAutoSave на каждое движение пальца, то есть таймер отменяется
    // десятки раз в секунду. Без NonCancellable cancel() мог попасть В СЕРЕДИНУ записи
    // (файл уже записан, строка Room ещё нет) — и повторить ту рассинхронизацию,
    // от которой защищает порядок вызовов в BoardRepositoryImpl.
    private fun scheduleAutoSave(regenerateThumbnail: Boolean) {
        val boardId = currentBoardId ?: return
        hasUnsavedChanges = true
        // Запрос на миниатюру не должен теряться при схлопывании нескольких правок.
        pendingThumbnailRequest = pendingThumbnailRequest || regenerateThumbnail
        saveTimerJob?.cancel()
        saveTimerJob = viewModelScope.launch(dispatcher) {
            delay(AUTO_SAVE_DEBOUNCE)
            val wantThumbnail = pendingThumbnailRequest
            pendingThumbnailRequest = false
            // NonCancellable: отмена таймера не должна оборвать уже начатую запись.
            // Mutex: две записи не должны накладываться на один файл.
            withContext(NonCancellable) {
                saveMutex.withLock {
                    persist(boardId = boardId, regenerateThumbnail = wantThumbnail)
                }
            }
        }
    }

    // Размер и кламп идут парой: controller запоминает размер экрана (нужен вставке
    // картинок и отпину), а pinned-картинки сразу вписываются в новые границы.
    fun updateCanvasViewportSize(widthPx: Int, heightPx: Int) {
        viewportController.updateCanvasSize(widthPx, heightPx)
        imageController.clampPinnedImagesToViewport(widthPx, heightPx)
    }

    fun onTransform(panX: Float, panY: Float, zoom: Float, centroidX: Float, centroidY: Float) =
        viewportController.onTransform(panX, panY, zoom, centroidX, centroidY)

    fun toggleZoomLock() = viewportController.toggleZoomLock()
    fun resetViewport() = viewportController.resetViewport()

    /**
     * Выдаёт id для штриха, который сейчас рисуется на холсте.
     *
     * Холст создаёт штрих в момент коммита (см. InfiniteCanvasView.nextStrokeId),
     * поэтому id обязан приходить отсюда: только ViewModel знает генератор,
     * засиженный под текущую доску.
     */
    fun nextStrokeId(): Long = idGenerator.next()

    fun createTempCameraFile(): File = imageController.createTempCameraFile()
    // boardId может прийти раньше openBoard (камера/галерея на старте), поэтому
    // запоминаем его — иначе автосейв не узнает текущую доску.
    fun insertImageFromUri(boardId: Long, uri: Uri) {
        if (boardId > 0L && currentBoardId == null) currentBoardId = boardId
        imageController.insertImageFromUri(boardId, uri)
    }

    fun selectImage(imageId: Long?) = imageController.selectImage(imageId)
    fun updateImageBounds(imageId: Long, newBounds: WorldBounds) =
        imageController.updateImageBounds(imageId, newBounds)

    fun deleteSelectedImage() {
        val boardId = currentBoardId ?: return
        imageController.deleteSelectedImage(boardId)
    }
    fun togglePinSelectedImage() = imageController.togglePinSelectedImage()

    fun selectBrushType(type: BrushType) = toolController.selectBrushType(type)
    fun selectEraser() = toolController.selectEraser()
    fun selectLassoTool() = toolController.selectLassoTool()
    fun selectColor(colorArgb: Int) = toolController.selectColor(colorArgb)
    fun selectPresetIndex(index: Int?) = toolController.selectPresetIndex(index)
    fun updatePresetColorAt(index: Int, newColorArgb: Int) =
        toolController.updatePresetColorAt(index, newColorArgb)

    fun pinCurrentColor(colorArgb: Int) = toolController.pinCurrentColor(colorArgb)
    fun removePresetColor(index: Int) = toolController.removePresetColor(index)
    fun setSnappedBrushSize(rawSize: Float, step: Float = 2f) =
        toolController.setSnappedBrushSize(rawSize, step)

    fun selectStrokes(ids: Set<Long>) = strokeController.selectStrokes(ids)
    fun deleteSelectedStrokes() = strokeController.deleteSelectedStrokes()
    fun moveSelectedStrokes(dx: Double, dy: Double) = strokeController.moveSelectedStrokes(dx, dy)
    fun copySelectedStrokes() = strokeController.copySelectedStrokes()
    fun pasteClipboardStrokes() = strokeController.pasteClipboardStrokes()
    fun onStrokeCommitted(stroke: Stroke) = strokeController.onStrokeCommitted(stroke)
    fun eraseStrokesByIds(idsToRemove: Set<Long>) = strokeController.eraseStrokesByIds(idsToRemove)
    fun undo() = strokeController.undo()

    fun clearSelection() {
        strokeController.clearSelection()
        imageController.clearSelection()
    }

    @SuppressLint("EmptySuperCall")
    // Финальное сохранение при уничтожении ViewModel.
    //
    // Раньше здесь вызывался flushSaveAndThumbnail(), но он запускался в viewModelScope,
    // а тот отменяется ДО onCleared — ветка была мёртвой и создавала ложное чувство
    // защиты. Теперь запись идёт в scope приложения (переживает ViewModel).
    // Условия пропуска: сохранённого доски нет, изменений нет, либо выход уже
    // начат через handleExit (тогда flush выполняется/выполнился отдельно).
    override fun onCleared() {
        super.onCleared()
        val boardId = currentBoardId
        if (boardId == null || !hasUnsavedChanges || isExiting) return
        applicationScope.launch {
            saveMutex.withLock {
                persist(boardId = boardId, regenerateThumbnail = true)
            }
        }
    }
}