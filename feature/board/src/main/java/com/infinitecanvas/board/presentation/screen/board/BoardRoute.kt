package com.infinitecanvas.board.presentation.screen.board

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinitecanvas.board.domain.port.BoardThumbnailer
import com.infinitecanvas.board.domain.port.IdGenerator
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.domain.repository.BoardRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Публичный вход в экран доски.
 *
 * Composition root (`:app`) передаёт только порты и scope приложения; ViewModel
 * и фабрика остаются деталью реализации модуля.
 */
@Composable
fun BoardRoute(
    boardId: Long,
    repository: BoardRepository,
    thumbnailGenerator: BoardThumbnailer,
    imageStore: ImageStore,
    idGenerator: IdGenerator,
    applicationScope: CoroutineScope,
    onNavigateBack: () -> Unit
) {
    val viewModel: BoardViewModel = viewModel(
        factory = BoardViewModelFactory(
            repository = repository,
            thumbnailGenerator = thumbnailGenerator,
            imageStore = imageStore,
            idGenerator = idGenerator,
            applicationScope = applicationScope
        )
    )
    BoardScreen(
        boardId = boardId,
        viewModel = viewModel,
        imageStore = imageStore,
        onNavigateBack = onNavigateBack
    )
}
