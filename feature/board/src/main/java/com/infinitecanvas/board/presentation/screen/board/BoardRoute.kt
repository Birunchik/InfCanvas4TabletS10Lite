package com.infinitecanvas.board.presentation.screen.board

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinitecanvas.board.domain.port.BoardThumbnailer
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.domain.repository.BoardRepository

/**
 * Публичный вход в экран доски.
 *
 * Composition root (`:app`) передаёт только порты; ViewModel и фабрика остаются
 * деталью реализации модуля.
 */
@Composable
fun BoardRoute(
    boardId: Long,
    repository: BoardRepository,
    thumbnailGenerator: BoardThumbnailer,
    imageStore: ImageStore,
    onNavigateBack: () -> Unit
) {
    val viewModel: BoardViewModel = viewModel(
        factory = BoardViewModelFactory(
            repository = repository,
            thumbnailGenerator = thumbnailGenerator,
            imageStore = imageStore
        )
    )
    BoardScreen(
        boardId = boardId,
        viewModel = viewModel,
        imageStore = imageStore,
        onNavigateBack = onNavigateBack
    )
}
