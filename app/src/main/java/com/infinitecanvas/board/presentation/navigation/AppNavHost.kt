package com.infinitecanvas.board.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.infinitecanvas.board.domain.port.BoardThumbnailer
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.domain.repository.BoardRepository
import com.infinitecanvas.board.presentation.screen.board.BoardScreen
import com.infinitecanvas.board.presentation.screen.board.BoardViewModel
import com.infinitecanvas.board.presentation.screen.board.BoardViewModelFactory
import com.infinitecanvas.board.presentation.screen.home.HomeScreen
import com.infinitecanvas.board.presentation.screen.home.HomeViewModel
import com.infinitecanvas.board.presentation.screen.home.HomeViewModelFactory

@Composable
fun AppNavHost(
    repository: BoardRepository,
    thumbnailGenerator: BoardThumbnailer,
    imageStore: ImageStore
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModelFactory(repository)
            )
            HomeScreen(
                viewModel = homeViewModel,
                onOpenBoard = { boardId ->
                    navController.navigate("board/$boardId") {
                        // launchSingleTop гасит двойной тап по карточке: иначе в бэкстеке две одинаковые доски
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "board/{boardId}",
            arguments = listOf(navArgument("boardId") { type = NavType.LongType })
        ) { backStackEntry ->
            // Тип аргумента объявлен LongType выше: без явного типа Navigation отдал бы строку и getLong вернул бы 0
            val boardId = backStackEntry.arguments?.getLong("boardId") ?: return@composable
            val boardViewModel: BoardViewModel = viewModel(
                factory = BoardViewModelFactory(
                    repository = repository,
                    thumbnailGenerator = thumbnailGenerator,
                    imageStore = imageStore
                )
            )
            BoardScreen(
                boardId = boardId,
                viewModel = boardViewModel,
                imageStore = imageStore,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}