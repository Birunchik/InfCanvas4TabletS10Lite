package com.infinitecanvas.board.presentation.screen.home

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinitecanvas.board.domain.repository.BoardRepository

/**
 * Публичный вход в экран списка досок.
 *
 * `:app` передаёт репозиторий и колбэк навигации; ViewModel/фабрика — деталь модуля.
 */
@Composable
fun HomeRoute(
    repository: BoardRepository,
    onOpenBoard: (Long) -> Unit
) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(repository)
    )
    HomeScreen(
        viewModel = viewModel,
        onOpenBoard = onOpenBoard
    )
}
