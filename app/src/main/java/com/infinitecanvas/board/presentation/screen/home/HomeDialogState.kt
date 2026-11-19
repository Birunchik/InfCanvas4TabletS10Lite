package com.infinitecanvas.board.presentation.screen.home

import androidx.compose.runtime.Immutable
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Folder

/**
 * Какой диалог сейчас открыт. Ровно один за раз — sealed-иерархия
 * исключает невозможные комбинации (флаг + nullable-объект), которые
 * возникали бы при независимых remember-переменных.
 *
 * None — обязательный первый член: без него var пришлось бы делать
 * nullable, и when потерял бы исчерпывающность.
 *
 * @Immutable — дети содержат immutable-модели (Folder, BoardSummary
 * помечены @Immutable в Фазе 4), безопасно передавать в Compose.
 */
@Immutable
sealed interface HomeDialogState {
    data object None : HomeDialogState
    data object CreateFolder : HomeDialogState
    data class RenameFolder(val folder: Folder) : HomeDialogState
    data class RenameBoard(val board: BoardSummary) : HomeDialogState
    data class MoveBoard(val board: BoardSummary) : HomeDialogState
    data class DeleteBoard(val board: BoardSummary) : HomeDialogState
    data class DeleteFolder(val folder: Folder) : HomeDialogState
}