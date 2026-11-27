package com.infinitecanvas.board

import android.content.Context
import coil.ImageLoader
import coil.memory.MemoryCache
import com.infinitecanvas.board.data.local.id.AtomicLongIdGenerator
import com.infinitecanvas.board.data.local.storage.BoardFileStorage
import com.infinitecanvas.board.data.local.storage.ImageAssetStorage
import com.infinitecanvas.board.data.local.storage.ThumbnailGenerator
import com.infinitecanvas.board.data.repository.createBoardRepository
import com.infinitecanvas.board.domain.port.BoardThumbnailer
import com.infinitecanvas.board.domain.port.IdGenerator
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.domain.repository.BoardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    // Сборка репозитория — через фабрику: DAO/BoardRepositoryImpl и Room остаются
    // деталью реализации core:data и не протекают в classpath :app.
    val repository: BoardRepository by lazy {
        createBoardRepository(
            context = appContext,
            fileStorage = fileStorage,
            imageStore = imageAssetStorage,
        )
    }

    private val fileStorage: BoardFileStorage by lazy { BoardFileStorage(appContext) }

    private val imageAssetStorage: ImageAssetStorage by lazy { ImageAssetStorage(appContext) }

    val imageStore: ImageStore get() = imageAssetStorage

    // Единственный генератор id на приложение. Сидируется при загрузке доски
    // (см. BoardViewModel.openBoard), поэтому новые id всегда идут после уже
    // сохранённых — уникальность в пределах доски.
    val idGenerator: IdGenerator by lazy { AtomicLongIdGenerator() }

    // Scope уровня приложения: переживает ViewModel и Activity, нужен для записи
    // при уничтожении ViewModel (onCleared), когда viewModelScope уже отменён.
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val thumbnailGenerator: BoardThumbnailer by lazy {
        ThumbnailGenerator(fileStorage = fileStorage, imageStore = imageAssetStorage)
    }
}