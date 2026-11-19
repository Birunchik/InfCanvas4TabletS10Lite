package com.infinitecanvas.board.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, BoardEntity::class],
    version = 1,
    exportSchema = false
)
// Схема v1 без миграций: при изменении entities нужен Migration (или явный
// fallback), иначе Room упадёт у пользователей с уже созданной БД.
abstract class AppDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // Double-checked locking: @Volatile обязателен, иначе возможен доступ к
        // частично сконструированному instance. Контекст — applicationContext.
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "infinite_canvas.db"
                ).build().also { instance = it }
            }
        }
    }
}