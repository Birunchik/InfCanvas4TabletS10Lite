package com.infinitecanvas.board.presentation.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitecanvas.board.domain.repository.BoardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
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
    private val _openBoardEvents = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openBoardEvents = _openBoardEvents.asSharedFlow()

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
        viewModelScope.launch {
            val newId = repository.createFolder(trimmed)
            // Сразу переключаем фильтр на новую папку, иначе пользователь не увидит, куда она создалась
            _selectedFilter.value = FolderFilter.Custom(newId, trimmed)
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.renameFolder(folderId, trimmed)
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            val current = _selectedFilter.value
            // Удалили активную папку — уходим на «Все», иначе останемся с фильтром на пустоту
            if (current is FolderFilter.Custom && current.folderId == folderId) {
                _selectedFilter.value = FolderFilter.All
            }
        }
    }

    fun createAndOpenBoard(defaultTitle: String) {
        val targetFolderId = (_selectedFilter.value as? FolderFilter.Custom)?.folderId
        viewModelScope.launch {
            val boardId = repository.createBoard(title = defaultTitle, folderId = targetFolderId)
            // tryEmit не приостанавливает корутину: буфер на 1, событие не должно блокировать создание доски
            _openBoardEvents.tryEmit(boardId)
        }
    }

    fun renameBoard(boardId: Long, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.renameBoard(boardId, trimmed)
        }
    }

    fun moveBoardToFolder(boardId: Long, targetFolderId: Long?) {
        viewModelScope.launch {
            repository.moveBoardToFolder(boardId, targetFolderId)
        }
    }

    fun deleteBoard(boardId: Long) {
        viewModelScope.launch {
            repository.deleteBoard(boardId)
        }
    }
}