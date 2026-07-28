package com.msphone.agent.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ai_history")
data class AiHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 用户原始输入 */
    val rawInput: String,
    /** 解析通道：LLM（在线）/ OFFLINE_FALLBACK（本地规则降级） */
    val sourceType: String,
    /** 结果类型：TASKS_CREATED / TEXT_REPLY / ERROR */
    val resultType: String,
    /** 结果摘要：任务标题+分类+提醒时间，或文本回复/错误信息（截断至 500 字符） */
    val resultSummary: String,
    /** 是否解析成功（resultType != ERROR） */
    val isSuccess: Boolean,
    /** 端到端耗时（毫秒），用于观察 GLM 响应性能 */
    val costMillis: Long,
    val createdAt: Long,
)

@Dao
interface AiHistoryDao {

    @Insert
    suspend fun insert(entity: AiHistoryEntity)

    @Query("SELECT * FROM ai_history ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AiHistoryEntity>>

    /** 保留最近 500 条，防止无限膨胀 */
    @Query(
        "DELETE FROM ai_history WHERE id NOT IN " +
            "(SELECT id FROM ai_history ORDER BY createdAt DESC LIMIT 500)"
    )
    suspend fun trim()
}
