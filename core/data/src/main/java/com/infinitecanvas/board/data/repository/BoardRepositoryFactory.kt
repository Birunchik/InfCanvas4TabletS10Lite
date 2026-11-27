package com.infinitecanvas.board.data.repository

import android.content.Context
import com.infinitecanvas.board.data.local.db.AppDatabase
import com.infinitecanvas.board.data.local.storage.BoardFileStorage
import com.infinitecanvas.board.domain.port.ImageStore
import com.infinitecanvas.board.domain.repository.BoardRepository

/**
 * Единственная точка сборки репозитория для composition root.
 *
 * Зачем фабрика, а не публичные [BoardRepositoryImpl] и DAO: DAO помечен
 * Room-аннотациями (`@Dao`, `@Query`), а публичный тип с такими аннотациями
 * заставляет держать `androidx.room:room-runtime` в `api` — иначе Kotlin
 * требует Room в compile classpath потребителей. Пока эти типы публичны,
 * Room протекает во все модули (это и фиксировал Dependency Analysis).
 *
 * Здесь база создаётся внутри модуля, наружу отдаётся только доменный интерфейс
 * [BoardRepository], поэтому Room остаётся `implementation`.
 */
fun createBoardRepository(
    context: Context,
    fileStorage: BoardFileStorage,
    imageStore: ImageStore
): BoardRepository {
    val dao = AppDatabase.getInstance(context.applicationContext).boardDao()
    return BoardRepositoryImpl(
        dao = dao,
        fileStorage = fileStorage,
        imageStore = imageStore
    )
}
