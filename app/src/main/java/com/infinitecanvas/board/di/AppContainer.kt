package com.infinitecanvas.board.di

import android.content.Context
import coil.ImageLoader
import coil.memory.MemoryCache
import com.infinitecanvas.board.data.local.db.AppDatabase
import com.infinitecanvas.board.data.local.storage.BoardFileStorage
import com.infinitecanvas.board.data.local.storage.ImageAssetStorage
import com.infinitecanvas.board.data.local.storage.ThumbnailGenerator
import com.infinitecanvas.board.data.repository.BoardRepositoryImpl

/**
 * Ручной composition root. Пока живёт в app; в Phase 1+ переедет рядом с App,
 * модули будут отдавать фабрики, а связывать будет только app.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // Ограничиваем кэш изображений Coil под 6/8 ГБ RAM Tab S10 Lite (15% доступной памяти)
    // ImageLoader задаётся глобально до setContent: без этого Coil создаст
    // дефолтный кэш, и ограничение памяти ниже не применится к превью.
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.15)
                    .build()
            }
            .build()
    }

    // Room хранит только метаданные, геометрия — в файлах (BoardFileStorage):
    // согласованность этих двух хранилищ обеспечивает репозиторий, не Room.
    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val fileStorage: BoardFileStorage by lazy { BoardFileStorage(appContext) }

    val imageAssetStorage: ImageAssetStorage by lazy { ImageAssetStorage(appContext) }

    val thumbnailGenerator: ThumbnailGenerator by lazy {
        ThumbnailGenerator(fileStorage = fileStorage, imageAssetStorage = imageAssetStorage)
    }

    val repository: BoardRepositoryImpl by lazy {
        BoardRepositoryImpl(
            dao = database.boardDao(),
            fileStorage = fileStorage,
            imageAssetStorage = imageAssetStorage,
        )
    }
}