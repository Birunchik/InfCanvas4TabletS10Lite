package com.infinitecanvas.board.presentation.screen.home

import com.infinitecanvas.board.R
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Folder
import com.infinitecanvas.board.testing.FakeBoardRepository
import com.infinitecanvas.board.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun board(id: Long, title: String, folderId: Long? = null) = BoardSummary(
        id = id,
        folderId = folderId,
        title = title,
        updatedAt = 0L,
        thumbnailPath = null,
        strokesCount = 0,
    )

    private fun folder(id: Long, name: String = "Work") = Folder(
        id = id,
        name = name,
        createdAt = 0L,
        sortOrder = 0,
    )

    /**
     * stateIn(WhileSubscribed) не эмитит без активного подписчика:
     * без этого хелпера uiState.value навсегда останется initialValue.
     */
    private fun kotlinx.coroutines.test.TestScope.subscribe(vm: HomeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect { /* just drain */ }
        }
    }

    // --- 1. initial state --------------------------------------------------

    @Test
    fun `initial state has empty folders and boards`() = runTest {
        val vm = HomeViewModel(FakeBoardRepository())
        subscribe(vm)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.folders.isEmpty())
        assertTrue(state.boards.isEmpty())
        assertEquals(FolderFilter.All, state.selectedFilter)
        assertEquals("", state.searchQuery)
    }

    // --- 2. selectFilter switches source ----------------------------------

    @Test
    fun `selectFilter switches boards source`() = runTest {
        val repo = FakeBoardRepository().apply {
            allBoardsFlow.value = listOf(board(1, "A"), board(2, "B"))
            unassignedBoardsFlow.value = listOf(board(2, "B"))
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.boards.size)

        vm.selectFilter(FolderFilter.Unassigned)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(FolderFilter.Unassigned, state.selectedFilter)
        assertEquals(listOf("B"), state.boards.map { it.title })
    }

    // --- 3. search filter --------------------------------------------------

    @Test
    fun `search filters boards by title case-insensitive`() = runTest {
        val repo = FakeBoardRepository().apply {
            allBoardsFlow.value = listOf(
                board(1, "Project Alpha"),
                board(2, "project beta"),
                board(3, "Notes"),
            )
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)
        advanceUntilIdle()

        vm.updateSearchQuery("PROJECT")
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), vm.uiState.value.boards.map { it.id })
        assertEquals("PROJECT", vm.uiState.value.searchQuery)
    }

    // --- 4. sync filter with folder deletion ------------------------------

    @Test
    fun `deleted folder resets filter to All`() = runTest {
        val repo = FakeBoardRepository().apply {
            foldersFlow.value = listOf(folder(7, "Work"))
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)
        advanceUntilIdle()

        vm.selectFilter(FolderFilter.Custom(7, "Work"))
        advanceUntilIdle()
        assertEquals(FolderFilter.Custom(7, "Work"), vm.uiState.value.selectedFilter)

        // Папку удалили извне: Room эмитит пустой список
        repo.foldersFlow.value = emptyList()
        advanceUntilIdle()

        assertEquals(FolderFilter.All, vm.uiState.value.selectedFilter)
    }

    // --- 5. createFolder: регрессионный тест (зелёный и сейчас) -----------

    @Test
    fun `createFolder sets Custom filter once folders stream catches up`() = runTest {
        val repo = FakeBoardRepository().apply {
            createFolderResult = { 42L }
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)
        advanceUntilIdle()

        vm.createFolder("Work")
        // Room ещё не эмитил новую папку — combine может откатить фильтр в All.
        // Этот тест не ловит мигание, он фиксирует ЖЕЛАЕМЫЙ финал после эмита.
        repo.foldersFlow.value = listOf(folder(42, "Work"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(FolderFilter.Custom(42, "Work"), state.selectedFilter)
        assertEquals(listOf("Work"), repo.createdFolders)
    }

    // --- 6. createAndOpenBoard emits one-shot event -----------------------

    @Test
    fun `createAndOpenBoard emits open event with created id`() = runTest {
        val repo = FakeBoardRepository().apply {
            createBoardResult = { _, _ -> 99L }
            // Фейк возвращает префикс как есть — фокус теста на событии, а не на имени.
            // Контракт "ViewModel использует результат getSuggestedBoardTitle" покрыт отдельным тестом.
            suggestedBoardTitleResult = { it }
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)

        val received = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.openBoardEvents.collect { received += it }
        }

        vm.createAndOpenBoard("Board 1")
        advanceUntilIdle()

        assertEquals(listOf(99L), received)
        assertEquals(listOf("Board 1" to null), repo.createdBoards)
    }

    @Test
    fun `createAndOpenBoard passes selected folder id`() = runTest {
        val repo = FakeBoardRepository().apply {
            foldersFlow.value = listOf(folder(7, "Work"))
            createBoardResult = { _, _ -> 99L }
            // Фейк возвращает префикс как есть — фокус теста на folderId.
            suggestedBoardTitleResult = { it }
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)
        advanceUntilIdle()

        vm.selectFilter(FolderFilter.Custom(7, "Work"))
        advanceUntilIdle()

        vm.createAndOpenBoard("Board 1")
        advanceUntilIdle()

        assertEquals(listOf("Board 1" to 7L), repo.createdBoards)
    }

    // --- 7. spy-проверки мутаций ------------------------------------------

    @Test
    fun `renameBoard trims and forwards to repository`() = runTest {
        val repo = FakeBoardRepository()
        val vm = HomeViewModel(repo)

        vm.renameBoard(5, "  New Name  ")
        advanceUntilIdle()

        assertEquals(listOf(5L to "New Name"), repo.renamedBoards)
    }

    @Test
    fun `renameBoard ignores blank title`() = runTest {
        val repo = FakeBoardRepository()
        val vm = HomeViewModel(repo)

        vm.renameBoard(5, "   ")
        advanceUntilIdle()

        assertTrue(repo.renamedBoards.isEmpty())
    }

    @Test
    fun `moveBoardToFolder forwards nullable target id`() = runTest {
        val repo = FakeBoardRepository()
        val vm = HomeViewModel(repo)

        vm.moveBoardToFolder(5, null)
        advanceUntilIdle()

        assertEquals(listOf(5L to null), repo.movedBoards)
    }

    @Test
    fun `deleteBoard forwards id`() = runTest {
        val repo = FakeBoardRepository()
        val vm = HomeViewModel(repo)

        vm.deleteBoard(5)
        advanceUntilIdle()

        assertEquals(listOf(5L), repo.deletedBoards)
    }

    @Test
    fun `renameFolder trims and forwards`() = runTest {
        val repo = FakeBoardRepository()
        val vm = HomeViewModel(repo)

        vm.renameFolder(7, "  Work  ")
        advanceUntilIdle()

        assertEquals(listOf(7L to "Work"), repo.renamedFolders)
    }

    @Test
    fun `deleteFolder of currently selected folder resets filter`() = runTest {
        val repo = FakeBoardRepository().apply {
            foldersFlow.value = listOf(folder(7, "Work"))
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)
        advanceUntilIdle()

        vm.selectFilter(FolderFilter.Custom(7, "Work"))
        advanceUntilIdle()

        vm.deleteFolder(7)
        advanceUntilIdle()

        assertEquals(listOf(7L), repo.deletedFolders)
        assertEquals(FolderFilter.All, vm.uiState.value.selectedFilter)
    }

    @Test
    fun `createAndOpenBoard uses suggested title from repository`() = runTest {
        val repo = FakeBoardRepository().apply {
            suggestedBoardTitleResult = { prefix -> "$prefix 42" }
            createBoardResult = { _, _ -> 99L }
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)

        val received = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.openBoardEvents.collect { received += it }
        }

        vm.createAndOpenBoard("Доска")
        advanceUntilIdle()

        assertEquals(listOf("Доска 42" to null), repo.createdBoards)
        assertEquals(listOf(99L), received)
    }

    @Test
    fun `createAndOpenBoard ignores visible boards count when search is active`() = runTest {
        // Регрессия: при поиске state.boards содержит только совпадения,
        // но имя должно считаться по всей БД через getSuggestedBoardTitle.
        // Данные подобраны так, чтобы "Alpha" не было подстрокой других названий.
        val repo = FakeBoardRepository().apply {
            allBoardsFlow.value = listOf(
                board(1, "Project Alpha"),
                board(2, "Project Beta"),
                board(3, "Project Gamma"),
            )
            suggestedBoardTitleResult = { prefix -> "$prefix 4" }   // max(3) + 1
            createBoardResult = { _, _ -> 99L }
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)
        advanceUntilIdle()

        vm.updateSearchQuery("Alpha")
        advanceUntilIdle()
        // Видима только одна доска — если бы ViewModel считала по state.boards,
        // получилось бы "Project 2", а не "Project 4".
        assertEquals(1, vm.uiState.value.boards.size)

        vm.createAndOpenBoard("Project")
        advanceUntilIdle()

        assertEquals(listOf("Project 4" to null), repo.createdBoards)
    }
    @Test
    fun `createFolder does not show boards from folder before folders stream includes it`() = runTest {
        // Регрессия на гонку: после repository.createFolder id уже известен, но Room
        // ещё не эмитил новую папку. В этот момент flatMapLatest уже переключился
        // на observeBoardsByFolder(newId), а syncedFilter в combine откатывает
        // заголовок в All — грид показывает доски папки, заголовок говорит "Все".
        val repo = FakeBoardRepository().apply {
            createFolderResult = { 42L }
            // В папке 42 уже есть доска — она и «просочится» в грид при баге
            boardsByFolderFlow.value = mapOf(42L to listOf(board(1, "Board A")))
            // allBoardsFlow пуст: если фильтр остался All, грид должен быть пуст
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)
        advanceUntilIdle()

        vm.createFolder("Work")
        advanceUntilIdle()

        // Bug: boards = [Board A] из папки 42, но selectedFilter = All (syncedFilter).
        // Fix: boards = [] — фильтр ещё All, observeAllBoards пуст.
        assertEquals(0, vm.uiState.value.boards.size)

        // Завершаем suspended-корутину createFolder (после фикса она ждёт эмита папки)
        repo.foldersFlow.value = listOf(folder(42, "Work"))
        advanceUntilIdle()

        assertEquals(FolderFilter.Custom(42, "Work"), vm.uiState.value.selectedFilter)
        assertEquals(1, vm.uiState.value.boards.size)
    }
    @Test
    fun `createFolder emits error event on repository failure`() = runTest {
        val repo = FakeBoardRepository().apply {
            createFolderThrows = RuntimeException("DB unavailable")
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)

        val errors = mutableListOf<HomeError>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.errorEvents.collect { errors += it }
        }

        vm.createFolder("Work")
        advanceUntilIdle()

        assertEquals(1, errors.size)
        assertEquals(R.string.error_operation_failed, errors[0].messageResId)
        // Побочные эффекты не выполнились — папка не создана, фильтр не переключён
        assertTrue(repo.createdFolders.isEmpty())
        assertEquals(FolderFilter.All, vm.uiState.value.selectedFilter)
    }

    @Test
    fun `createAndOpenBoard emits error and no open event on failure`() = runTest {
        val repo = FakeBoardRepository().apply {
            createBoardThrows = RuntimeException("DB unavailable")
        }
        val vm = HomeViewModel(repo)
        subscribe(vm)

        val opened = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.openBoardEvents.collect { opened += it }
        }
        val errors = mutableListOf<HomeError>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.errorEvents.collect { errors += it }
        }

        vm.createAndOpenBoard("Board")
        advanceUntilIdle()

        assertTrue(opened.isEmpty())          // навигация не должна произойти
        assertEquals(1, errors.size)
        assertTrue(repo.createdBoards.isEmpty())
    }
}