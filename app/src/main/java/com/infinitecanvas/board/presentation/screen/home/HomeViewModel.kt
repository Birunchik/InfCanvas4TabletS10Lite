package com.infinitecanvas.board.presentation.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitecanvas.board.R
import com.infinitecanvas.board.domain.repository.BoardRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Состояние экрана собирается одним combine: порядок эмита предсказуем,
 * а фильтр и поиск переживают пересоздание Activity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: BoardRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<FolderFilter>(FolderFilter.All)
    private val _searchQuery = MutableStateFlow("")

    // Одноразовое событие: SharedFlow, а не StateFlow, иначе повторная навигация при рекомпозиции
    private val _openBoardEvents = MutableSharedFlow<Long>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val openBoardEvents = _openBoardEvents.asSharedFlow()

    private val _errorEvents = MutableSharedFlow<HomeError>(extraBufferCapacity = 1)
    val errorEvents: SharedFlow<HomeError> = _errorEvents.asSharedFlow()

    private val errorHandler = CoroutineExceptionHandler { _, throwable ->
        _errorEvents.tryEmit(HomeError(R.string.error_operation_failed, throwable))
    }

    // flatMapLatest отменяет прошлую подписку на БД: без него при смене фильтра копились бы живые коллекторы
    private val boardsFlow = _selectedFilter.flatMapLatest { filter ->
        when (filter) {
            FolderFilter.All -> repository.observeAllBoards()
            FolderFilter.Unassigned -> repository.observeUnassignedBoards()
            is FolderFilter.Custom -> repository.observeBoardsByFolder(filter.folderId)
        }
    }

    // Четыре источника: любой (папки, фильтр, поиск) пересобирает состояние целиком, без промежуточных мутаций
    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeFolders(),
        boardsFlow,
        _selectedFilter,
        _searchQuery
    ) { folders, boards, filter, query ->
        // Папку могли удалить или переименовать извне: без сверки фильтр останется указывать на несуществующий id
        val syncedFilter = if (filter is FolderFilter.Custom) {
            val actualFolder = folders.find { it.id == filter.folderId }
            if (actualFolder != null) {
                FolderFilter.Custom(actualFolder.id, actualFolder.name)
            } else {
                FolderFilter.All
            }
        } else {
            filter
        }

        // Поиск — подстрока без нормализации: «ё» не считается «е», замена регистра не спасает
        val filteredBoards = if (query.isBlank()) {
            boards
        } else {
            val q = query.trim()
            boards.filter { it.title.contains(q, ignoreCase = true) }
        }

        HomeUiState(
            folders = folders,
            boards = filteredBoards,
            selectedFilter = syncedFilter,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun selectFilter(filter: FolderFilter) {
        _selectedFilter.value = filter
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(errorHandler) {
            val newId = repository.createFolder(trimmed)
            repository.observeFolders()
                .first { list -> list.any { it.id == newId } }
            _selectedFilter.value = FolderFilter.Custom(newId, trimmed)
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(errorHandler) {
            repository.renameFolder(folderId, trimmed)
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch(errorHandler) {
            repository.deleteFolder(folderId)
            val current = _selectedFilter.value
            if (current is FolderFilter.Custom && current.folderId == folderId) {
                _selectedFilter.value = FolderFilter.All
            }
        }
    }

    fun createAndOpenBoard(defaultTitlePrefix: String) {
        val targetFolderId = (_selectedFilter.value as? FolderFilter.Custom)?.folderId
        viewModelScope.launch(errorHandler) {
            val title = repository.getSuggestedBoardTitle(defaultTitlePrefix)
            val boardId = repository.createBoard(title = title, folderId = targetFolderId)
            _openBoardEvents.tryEmit(boardId)
        }
    }

    fun renameBoard(boardId: Long, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(errorHandler) {
            repository.renameBoard(boardId, trimmed)
        }
    }

    fun moveBoardToFolder(boardId: Long, targetFolderId: Long?) {
        viewModelScope.launch(errorHandler) {
            repository.moveBoardToFolder(boardId, targetFolderId)
        }
    }

    fun deleteBoard(boardId: Long) {
        viewModelScope.launch(errorHandler) {
            repository.deleteBoard(boardId)
        }
    }
}