package com.infinitecanvas.board.presentation.screen.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.infinitecanvas.feature.home.R
import com.infinitecanvas.board.domain.model.BoardSummary
import com.infinitecanvas.board.domain.model.Folder
import com.infinitecanvas.board.presentation.theme.ui.InfCanvasTheme
import com.infinitecanvas.board.testing.FakeBoardRepository
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun board(id: Long, title: String, folderId: Long? = null) = BoardSummary(
        id = id,
        folderId = folderId,
        title = title,
        updatedAt = 0L,
        thumbnailPath = null,
        strokesCount = 0,
    )

    private fun folder(id: Long = 7L, name: String = "Work") = Folder(
        id = id,
        name = name,
        sortOrder = 0,
        createdAt = 0L,
    )

    private fun setHomeScreen(vm: HomeViewModel) {
        composeRule.setContent {
            InfCanvasTheme {
                HomeScreen(viewModel = vm) {}
            }
        }
        // StateFlow-цепочка (combine + stateIn) эмитит асинхронно на Main.
        // waitForIdle дожидается, пока Compose применит финальное состояние.
        composeRule.waitForIdle()
    }

    @Test
    fun emptyState_noBoardsAtAll_showsAllBoardsTitle() {
        val repo = FakeBoardRepository().apply {
            foldersFlow.value = emptyList()
            allBoardsFlow.value = emptyList()
        }
        val vm = HomeViewModel(repo)
        setHomeScreen(vm)

        composeRule
            .onNodeWithText(context.getString(R.string.empty_boards_title))
            .assertIsDisplayed()
    }

    @Test
    fun emptyState_emptyCustomFolder_showsFolderTitle() {
        val repo = FakeBoardRepository().apply {
            foldersFlow.value = listOf(folder())
            // boardsByFolderFlow[7] по умолчанию = emptyList()
        }
        val vm = HomeViewModel(repo)
        vm.selectFilter(FolderFilter.Custom(7, "Work"))
        setHomeScreen(vm)

        composeRule
            .onNodeWithText(context.getString(R.string.empty_folder_title))
            .assertIsDisplayed()
    }

    @Test
    fun emptyState_searchWithNoMatches_showsSearchTitle() {
        val repo = FakeBoardRepository().apply {
            foldersFlow.value = emptyList()
            allBoardsFlow.value = listOf(board(1, "Alpha"))
        }
        val vm = HomeViewModel(repo)
        vm.updateSearchQuery("zzz")   // ни одна доска не совпадает
        setHomeScreen(vm)

        composeRule
            .onNodeWithText(context.getString(R.string.empty_search_title))
            .assertIsDisplayed()
        // Подзаголовок другой — убеждаемся, что это именно search-ветка
        composeRule
            .onNodeWithText(context.getString(R.string.empty_search_subtitle))
            .assertIsDisplayed()
    }
}