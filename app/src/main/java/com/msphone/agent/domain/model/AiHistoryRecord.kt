package com.msphone.agent.domain.model

/** AI 解析通道 */
enum class AiSourceType { LLM, OFFLINE_FALLBACK }

/** AI 解析结果类型 */
enum class AiResultType { TASKS_CREATED, TEXT_REPLY, ERROR }

/** 一次 AI 解析的历史记录（回溯任务来源 / 评估解析质量） */
data class AiHistoryRecord(
    val id: Long = 0,
    val rawInput: String,
    val sourceType: AiSourceType,
    val resultType: AiResultType,
    val resultSummary: String,
    val isSuccess: Boolean = resultType != AiResultType.ERROR,
    val costMillis: Long,
    val createdAt: Long = System.currentTimeMillis(),
)
