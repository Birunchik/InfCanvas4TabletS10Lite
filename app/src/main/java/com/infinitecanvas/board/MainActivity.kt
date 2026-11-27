package com.infinitecanvas.board

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil.Coil
import com.infinitecanvas.board.presentation.theme.ui.InfCanvasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = AppContainer(this)
        Coil.setImageLoader(container.imageLoader)

        // Зависимости передаются готовыми: контент setContent пересоздаётся при
        // рекомпозиции, а создавать Room/репозиторий внутри composable нельзя — дубликаты.
        setContent {
            InfCanvasTheme {
                AppNavHost(
                    repository = container.repository,
                    thumbnailGenerator = container.thumbnailGenerator,
                    imageStore = container.imageStore,
                    idGenerator = container.idGenerator,
                    applicationScope = container.applicationScope,
                )
            }
        }
    }
}
