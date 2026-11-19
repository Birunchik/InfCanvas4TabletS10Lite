package com.infinitecanvas.board

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import com.infinitecanvas.board.data.local.db.AppDatabase
import com.infinitecanvas.board.data.local.storage.BoardFileStorage
import com.infinitecanvas.board.data.local.storage.ImageAssetStorage
import com.infinitecanvas.board.data.local.storage.ThumbnailGenerator
import com.infinitecanvas.board.data.repository.BoardRepositoryImpl
import com.infinitecanvas.board.presentation.navigation.AppNavHost

// Точка сборки зависимостей: DI-фреймворка нет, поэтому всё создаётся здесь
// один раз на Activity и переживает пересоздание Compose-дерева, но не процесса.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ограничиваем кэш изображений Coil под 6/8 ГБ RAM Tab S10 Lite (15% доступной памяти)
        // ImageLoader задаётся глобально до setContent: без этого Coil создаст
        // дефолтный кэш, и ограничение памяти ниже не применится к превью.
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)

        // Room хранит только метаданные, геометрия — в файлах (BoardFileStorage):
        // согласованность этих двух хранилищ обеспечивает репозиторий, не Room.
        val database = AppDatabase.getInstance(applicationContext)
        val fileStorage = BoardFileStorage(applicationContext)
        val imageAssetStorage = ImageAssetStorage(applicationContext)
        val thumbnailGenerator = ThumbnailGenerator(
            fileStorage = fileStorage,
            imageAssetStorage = imageAssetStorage
        )
        val repository = BoardRepositoryImpl(
            dao = database.boardDao(),
            fileStorage = fileStorage,
            imageAssetStorage = imageAssetStorage
        )

        // Зависимости передаются готовыми: контент setContent пересоздаётся при
        // рекомпозиции, а создавать Room/репозиторий внутри composable нельзя — дубликаты.
        setContent {
            MaterialTheme {
                AppNavHost(
                    repository = repository,
                    thumbnailGenerator = thumbnailGenerator,
                    imageAssetStorage = imageAssetStorage
                )
            }
        }
    }
}