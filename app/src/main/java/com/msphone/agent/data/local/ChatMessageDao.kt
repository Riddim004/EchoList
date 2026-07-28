package com.msphone.agent.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,        // USER / ASSISTANT / TASK_CARD / DIVIDER
    val content: String,
    val isError: Boolean,
    val taskId: Long?,       // TASK_CARD 关联的任务 id
    val draftJson: String?,  // TASK_CARD 的 TaskDraft 序列化 JSON
    val undone: Boolean,
    val createdAt: Long,
)

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    /** 最后一条 DIVIDER 之后的记录（当前上下文窗口的候选） */
    @Query(
        "SELECT * FROM chat_messages WHERE id > " +
            "COALESCE((SELECT MAX(id) FROM chat_messages WHERE type = 'DIVIDER'), 0) " +
            "ORDER BY id ASC"
    )
    suspend fun getAfterLastDivider(): List<ChatMessageEntity>

    @Insert
    suspend fun insert(entity: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET undone = 1 WHERE id = :id")
    suspend fun markUndone(id: Long)
}
