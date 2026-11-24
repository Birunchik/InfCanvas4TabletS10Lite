package com.infinitecanvas.board.presentation.screen.board

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.infinitecanvas.board.domain.model.BoardDocument
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Brush
import com.infinitecanvas.board.domain.model.Stroke
import com.infinitecanvas.board.domain.model.StrokePoint
import com.infinitecanvas.board.domain.model.ViewportState
import com.infinitecanvas.board.testing.FakeBoardRepository
import com.infinitecanvas.board.testing.FakeBoardThumbnailer
import com.infinitecanvas.board.testing.FakeImageStore
import com.infinitecanvas.board.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
    private lateinit var store: ViewModelStore
    private lateinit var vm: BoardViewModel

    @Before
    fun setUp() {
        repository = FakeBoardRepository()
        thumbnailer = FakeBoardThumbnailer()
        imageStore = FakeImageStore()
        store = ViewModelStore()
        val factory = BoardViewModelFactory(repository, thumbnailer, imageStore)
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