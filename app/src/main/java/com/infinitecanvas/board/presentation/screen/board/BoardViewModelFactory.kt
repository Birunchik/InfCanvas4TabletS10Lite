package com.infinitecanvas.board.presentation.screen.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.infinitecanvas.board.data.local.storage.ThumbnailGenerator
import com.infinitecanvas.board.domain.repository.BoardRepository

class BoardViewModelFactory(
    private val repository: BoardRepository,
    private val thumbnailGenerator: ThumbnailGenerator
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BoardViewModel::class.java)) {
            return BoardViewModel(
                repository = repository,
                thumbnailGenerator = thumbnailGenerator
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}