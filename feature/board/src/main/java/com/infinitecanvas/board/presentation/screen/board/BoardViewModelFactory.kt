package com.infinitecanvas.board.presentation.screen.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.infinitecanvas.board.domain.port.BoardThumbnailer
import com.infinitecanvas.board.domain.port.IdGenerator
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.domain.repository.BoardRepository
import kotlinx.coroutines.CoroutineScope

internal class BoardViewModelFactory(
    private val repository: BoardRepository,
    private val thumbnailGenerator: BoardThumbnailer,
    private val imageStore: ImageStore,
    private val idGenerator: IdGenerator,
    private val applicationScope: CoroutineScope
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    // isAssignableFrom — чтобы фабрика работала и для подклассов BoardViewModel;
    // неизвестная модель — исключение, а не молчаливый null из фабрики.
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BoardViewModel::class.java)) {
            return BoardViewModel(
                repository = repository,
                thumbnailGenerator = thumbnailGenerator,
                imageStore = imageStore,
                idGenerator = idGenerator,
                applicationScope = applicationScope
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
