package com.msphone.agent.data.repository

import com.msphone.agent.data.local.ChatMessageDao
import com.msphone.agent.data.local.ChatMessageEntity
import com.msphone.agent.domain.model.ChatEntry
import com.msphone.agent.domain.model.ChatEntryType
import com.msphone.agent.domain.model.TaskDraft
import com.msphone.agent.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val dao: ChatMessageDao,
    private val json: Json,
) : ChatRepository {

    override fun observeAll(): Flow<List<ChatEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getCurrentContext(): List<ChatEntry> =
        dao.getAfterLastDivider().map { it.toDomain() }

    override suspend fun insert(entry: ChatEntry): Long = dao.insert(entry.toEntity())

    override suspend fun markUndone(entryId: Long) = dao.markUndone(entryId)

    override suspend fun insertDivider() {
        dao.insert(ChatEntry(type = ChatEntryType.DIVIDER).toEntity())
    }

    override suspend fun prune(retentionMillis: Long) {
        val cutoff = System.currentTimeMillis() - retentionMillis
        // 清理边界：当前会话起点（最后一条分隔线）；从未开过新对话时保底留最近 KEEP_MIN_ENTRIES 条
        val boundary = dao.lastDividerId()
            ?: dao.nthNewestId(KEEP_MIN_ENTRIES - 1)
            ?: return
        dao.deleteBefore(boundary, cutoff)
    }

    override suspend fun clearAll() = dao.deleteAll()

    // ---------- mapping ----------

    private fun ChatMessageEntity.toDomain(): ChatEntry = ChatEntry(
        id = id,
        type = runCatching { ChatEntryType.valueOf(type) }.getOrDefault(ChatEntryType.ASSISTANT),
        content = content,
        isError = isError,
        taskId = taskId,
        draft = draftJson?.let { raw ->
            runCatching { json.decodeFromString<TaskDraft>(raw) }.getOrNull()
        },
        undone = undone,
        createdAt = createdAt,
    )

    private fun ChatEntry.toEntity(): ChatMessageEntity = ChatMessageEntity(
        id = id,
        type = type.name,
        content = content,
        isError = isError,
        taskId = taskId,
        draftJson = draft?.let { json.encodeToString(TaskDraft.serializer(), it) },
        undone = undone,
        createdAt = createdAt,
    )

    private companion object {
        /** 与 TaskAgent.MAX_HISTORY_ENTRIES 对齐：至少保留一个完整滑动窗口 */
        const val KEEP_MIN_ENTRIES = 20
    }
}
