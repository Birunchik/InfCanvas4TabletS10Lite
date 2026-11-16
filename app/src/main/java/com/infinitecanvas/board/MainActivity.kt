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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ограничиваем кэш изображений Coil под 6/8 ГБ RAM Tab S10 Lite (15% доступной памяти)
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)

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