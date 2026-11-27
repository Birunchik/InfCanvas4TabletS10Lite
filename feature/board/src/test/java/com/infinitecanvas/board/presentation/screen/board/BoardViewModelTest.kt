package com.infinitecanvas.board.presentation.screen.board

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.infinitecanvas.board.domain.model.BoardDocument
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.domain.model.WorldBounds
import com.infinitecanvas.board.testing.FakeBoardRepository
import com.infinitecanvas.board.testing.FakeBoardThumbnailer
import com.infinitecanvas.board.testing.FakeIdGenerator
import com.infinitecanvas.board.testing.FakeImageStore
import com.infinitecanvas.board.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    // StandardTestDispatcher: дебаунс и delay управляемы через advanceTimeBy.
    // Дефолт MainDispatcherRule (UnconfinedTestDispatcher) для теста дебаунса
    // не подходит: задачи выполнялись бы синхронно, виртуальное время не играло бы роли.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var repository: FakeBoardRepository
    private lateinit var thumbnailer: FakeBoardThumbnailer
    private lateinit var imageStore: FakeImageStore
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var store: ViewModelStore
    private lateinit var vm: BoardViewModel

    // Scope «приложения» для записи из onCleared. Тестовый планировщик, чтобы
    // финальная запись управлялась тем же виртуальным временем, что и остальные.
    private lateinit var appScope: CoroutineScope

    @Before
    fun setUp() {
        repository = FakeBoardRepository()
        thumbnailer = FakeBoardThumbnailer()
        imageStore = FakeImageStore()
        idGenerator = FakeIdGenerator()
        store = ViewModelStore()
        appScope = CoroutineScope(SupervisorJob() + mainDispatcherRule.testDispatcher)
        val factory = BoardViewModelFactory(repository, thumbnailer, imageStore, idGenerator, appScope)
        // create(...) вместо конструктора: в lifecycle 2.11 конструктор deprecated.
        vm = ViewModelProvider.create(store, factory)[BoardViewModel::class.java]
    }

    // store.clear() намеренно не в tearDown: onCleared() при hasUnsavedChanges=true
    // сам вызовет flushSaveAndThumbnail и добавит saveCalls в тестах, которым он
    // не нужен. Тест 4 вызывает store.clear() явно — это его сценарий.

    // ── Тест 1: openBoard-guard ──
    @Test
    fun openBoard_sameIdTwice_loadsOnce() = runTest(mainDispatcherRule.testDispatcher) {
        repository.boardToLoad = emptyDocument()

        vm.openBoard(1L)
        advanceUntilIdle()

        vm.openBoard(1L)
        advanceUntilIdle()

        assertEquals(listOf(1L), repository.loadCalls)
    }

    // ── Тест 2: дебаунс 1.5 с ──
    @Test
    fun autoSave_debounces1500ms() = runTest(mainDispatcherRule.testDispatcher) {
        repository.boardToLoad = emptyDocument()
        vm.openBoard(1L)
        advanceUntilIdle()

        // Первое изменение: launch(dispatcher) стартует на t=0, delay регистрируется на t=1500.
        vm.onStrokeCommitted(sampleStroke(1L))
        advanceTimeBy(1_000.milliseconds) // t=1000 — job1 не сработал

        // Второе изменение: job1 отменён, job2 стартует на t=1000, delay на t=2500.
        vm.onStrokeCommitted(sampleStroke(2L))
        advanceTimeBy(1_000.milliseconds) // t=2000 — job2 не сработал

        assertTrue("дебаунс не должен сработать до t=2500", repository.saveCalls.isEmpty())

        advanceTimeBy(600.milliseconds) // t=2600 > 2500 — job2 срабатывает
        assertEquals(1, repository.saveCalls.size)
        assertEquals(2, repository.saveCalls[0].strokes.size)
    }

    // ── Тест 3: двойной flush ──
    @Test
    fun flush_twice_savesOnce_andBothCallbacks() = runTest(mainDispatcherRule.testDispatcher) {
        repository.boardToLoad = emptyDocument()
        vm.openBoard(1L)
        advanceUntilIdle()

        var cb1 = 0
        var cb2 = 0
        vm.flushSaveAndThumbnail { cb1++ }
        vm.flushSaveAndThumbnail { cb2++ }
        advanceUntilIdle()

        assertEquals("второй flush не должен писать повторно", 1, repository.saveCalls.size)
        assertEquals("первый колбэк — после save", 1, cb1)
        assertEquals("второй колбэк — сразу, без ожидания", 1, cb2)
    }

    // ── Тест 4: NonCancellable ──
    @Test
    fun flush_saveCompletesAfterScopeCancel() = runTest(mainDispatcherRule.testDispatcher) {
        repository.boardToLoad = emptyDocument()
        vm.openBoard(1L)
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate
        vm.flushSaveAndThumbnail()
        advanceUntilIdle() // дошли до saveBoardContent, запаркованы на await()

        assertTrue("save ещё не должен быть записан", repository.saveCalls.isEmpty())

        store.clear() // onCleared → viewModelScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            "save должен завершиться несмотря на отмену scope",
            1,
            repository.saveCalls.size
        )
    }

    // ── Тест: генератор id сидируется под загруженную доску ──
    @Test
    fun openBoard_seedsIdGeneratorAboveSavedIds() = runTest(mainDispatcherRule.testDispatcher) {
        // В доске уже есть штрих с id=500 и картинка с id=900: новые id обязаны
        // идти после них, иначе новый штрих получил бы занятый id и потерялся бы
        // в quadtree (HashMap по id).
        repository.boardToLoad = emptyDocument().copy(
            strokes = listOf(sampleStroke(500L)),
            images = listOf(
                BoardImage(
                    id = 900L,
                    fileName = "img.webp",
                    bounds = WorldBounds(0.0, 0.0, 10.0, 10.0)
                )
            )
        )

        vm.openBoard(1L)
        advanceUntilIdle()

        // Сид взят по максимуму из штрихов И картинок.
        assertEquals(900L, idGenerator.peekCurrent())
        assertEquals(901L, vm.nextStrokeId())
    }

    @Test
    fun openBoard_withEmptyBoard_startsFromOne() = runTest(mainDispatcherRule.testDispatcher) {
        repository.boardToLoad = emptyDocument()

        vm.openBoard(1L)
        advanceUntilIdle()

        assertEquals(1L, vm.nextStrokeId())
    }

    @Test
    fun openBoard_secondBoardWithHigherIds_raisesGenerator() = runTest(mainDispatcherRule.testDispatcher) {
        repository.boardToLoad = emptyDocument().copy(strokes = listOf(sampleStroke(10L)))
        vm.openBoard(1L)
        advanceUntilIdle()
        assertEquals(11L, vm.nextStrokeId()) // 12 после выдачи

        // Открываем другую доску с бо́льшими id — генератор обязан подняться.
        repository.boardToLoad = emptyDocument().copy(strokes = listOf(sampleStroke(7_000L)))
        vm.openBoard(2L)
        advanceUntilIdle()

        assertTrue("счётчик должен догнать новую доску", idGenerator.peekCurrent() >= 7_000L)
        assertTrue("новый id обязан быть больше сохранённых", vm.nextStrokeId() > 7_000L)
    }

    // ── Тест: пан во время записи не обрывает её ──
    @Test
    fun autoSave_viewportChangeDuringWrite_doesNotAbortWrite() = runTest(mainDispatcherRule.testDispatcher) {
        repository.boardToLoad = emptyDocument()
        vm.openBoard(1L)
        advanceUntilIdle()

        // Инициируем контентную правку и доводим таймер до срабатывания.
        vm.onStrokeCommitted(sampleStroke(1L))

        // Держим запись «в полёте»: saveBoardContent запаркуется на gate.
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate
        advanceTimeBy(1_600.milliseconds)
        advanceUntilIdle() // дошли до saveBoardContent и стоим на await()

        // Пользователь панорамирует — на каждое движение прилетает новый запрос,
        // который раньше вызывал autoSaveJob.cancel() и мог оборвать начатую запись
        // (файл записан, Room — нет).
        vm.onTransform(panX = 10f, panY = 0f, zoom = 1f, centroidX = 0f, centroidY = 0f)
        vm.onTransform(panX = 10f, panY = 0f, zoom = 1f, centroidX = 0f, centroidY = 0f)

        gate.complete(Unit)
        // runCurrent, а не advanceUntilIdle: проверяем именно УЖЕ НАЧАТУЮ запись,
        // не давая сработать таймеру от пана (иначе счётчик вырастет законно).
        runCurrent()

        assertEquals(
            "начатая запись должна дойти до конца несмотря на пан",
            1,
            repository.saveCalls.size
        )
        assertEquals("запись должна содержать закоммиченный штрих", 1, repository.saveCalls[0].strokes.size)
    }

    // ── Тест: правки во время записи не теряются ──
    @Test
    fun autoSave_changesDuringWrite_areSavedAfterWriteCompletes() = runTest(mainDispatcherRule.testDispatcher) {
        repository.boardToLoad = emptyDocument()
        vm.openBoard(1L)
        advanceUntilIdle()

        vm.onStrokeCommitted(sampleStroke(1L))
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate
        advanceTimeBy(1_600.milliseconds)
        advanceUntilIdle() // первая запись зависла на gate

        // Новая правка, пока запись идёт: обязана попасть в следующее сохранение.
        vm.onStrokeCommitted(sampleStroke(2L))
        gate.complete(Unit)
        repository.saveGate = null
        advanceUntilIdle()

        // Дебаунс новой правки должен дойти до второй записи.
        advanceTimeBy(1_700.milliseconds)
        advanceUntilIdle()

        assertTrue("должно быть две записи", repository.saveCalls.size >= 2)
        assertEquals(
            "вторая запись должна содержать оба штриха",
            2,
            repository.saveCalls.last().strokes.size
        )
    }

    // ── Тест: onCleared действительно сохраняет ──
    // Раньше эта ветка была мёртвой: flush вызывался в viewModelScope, который
    // к моменту onCleared уже отменён, поэтому launch не выполнялся вообще.
    @Test
    fun onCleared_persistsUnsavedChanges() = runTest(mainDispatcherRule.testDispatcher) {
        repository.boardToLoad = emptyDocument()
        vm.openBoard(1L)
        advanceUntilIdle()

        // Правка есть, но автосейв ещё не успел (дебаунс 1500 мс не истёк).
        vm.onStrokeCommitted(sampleStroke(1L))
        advanceTimeBy(200.milliseconds)
        assertTrue("автосейв ещё не должен был сработать", repository.saveCalls.isEmpty())

        // Уничтожаем ViewModel: viewModelScope отменяется до onCleared.
        store.clear()
        advanceUntilIdle()

        assertEquals(
            "onCleared обязан сохранить несохранённые правки в scope приложения",
            1,
            repository.saveCalls.size
        )
        assertEquals(1, repository.saveCalls[0].strokes.size)
    }

    // ── Тест: onCleared без изменений не пишет ──
    @Test
    fun onCleared_withoutChanges_doesNotSave() = runTest(mainDispatcherRule.testDispatcher) {
        repository.boardToLoad = emptyDocument()
        vm.openBoard(1L)
        advanceUntilIdle()

        store.clear()
        advanceUntilIdle()

        assertTrue("нечего сохранять — записи быть не должно", repository.saveCalls.isEmpty())
    }

    // ── helpers ──
    private fun emptyDocument() = BoardDocument(
        summary = BoardSummary(
            id = 1L,
            folderId = null,
            title = "Test board",
            updatedAt = 0L,
            thumbnailPath = null,
            strokesCount = 0
        ),
        strokes = emptyList(),
        images = emptyList(),
        viewport = ViewportState()
    )

    private fun sampleStroke(id: Long) = Stroke.create(
        id = id,
        points = listOf(
            StrokePoint(0.0, 0.0, 1f),
            StrokePoint(10.0, 10.0, 1f)
        ),
        brush = Brush()
    )
}