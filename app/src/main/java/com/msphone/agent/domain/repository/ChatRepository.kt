package com.msphone.agent.domain.repository

import com.msphone.agent.domain.model.ChatEntry
import kotlinx.coroutines.flow.Flow

/** 聊天记录仓库：UI 展示与模型上下文窗口共用 */
interface ChatRepository {

    /** 订阅全部聊天记录（含历史会话与分隔线） */
    fun observeAll(): Flow<List<ChatEntry>>

    /** 当前上下文窗口：最后一次"清空上下文"之后的记录 */
    suspend fun getCurrentContext(): List<ChatEntry>

    /** @return 新记录 id */
    suspend fun insert(entry: ChatEntry): Long

    /** 标记任务卡片为已撤销 */
    suspend fun markUndone(entryId: Long)

    /** 插入上下文分隔线（开启新会话） */
    suspend fun insertDivider()

    /**
     * 自动清理堆积的旧记录：删除超过保留期且不属于当前会话的消息。
     * 当前会话（最后一条分隔线之后）永不误删；无分隔线时保底保留最近的滑动窗口条数。
     */
    suspend fun prune(retentionMillis: Long)

    /** 清空全部聊天记录（任务数据不受影响） */
    suspend fun clearAll()
}
