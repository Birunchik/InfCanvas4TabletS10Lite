package com.infinitecanvas.board

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.infinitecanvas.board.domain.port.BoardThumbnailer
import com.infinitecanvas.board.domain.port.IdGenerator
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.domain.repository.BoardRepository
import com.infinitecanvas.board.presentation.screen.board.BoardRoute
import com.infinitecanvas.board.presentation.screen.home.HomeRoute
import kotlinx.coroutines.CoroutineScope

@Composable
fun AppNavHost(
    repository: BoardRepository,
    thumbnailGenerator: BoardThumbnailer,
    imageStore: ImageStore,
    idGenerator: IdGenerator,
    applicationScope: CoroutineScope
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeRoute(
                repository = repository,
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
            BoardRoute(
                boardId = boardId,
                repository = repository,
                thumbnailGenerator = thumbnailGenerator,
                imageStore = imageStore,
                idGenerator = idGenerator,
                applicationScope = applicationScope,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}