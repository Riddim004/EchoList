package com.msphone.agent.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, ChatMessageEntity::class, AiHistoryEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun aiHistoryDao(): AiHistoryDao
}
