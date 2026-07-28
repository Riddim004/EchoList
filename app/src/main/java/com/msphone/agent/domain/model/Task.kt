package com.msphone.agent.domain.model

import kotlinx.serialization.Serializable

/** 任务类别：工作 / 生活 */
enum class TaskCategory {
    WORK, LIFE;

    val label: String
        get() = when (this) {
            WORK -> "工作"
            LIFE -> "生活"
        }

    companion object {
        fun from(value: String?): TaskCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LIFE
    }
}

/** 任务状态 */
enum class TaskStatus { PENDING, DONE, EXPIRED }

/** 领域模型：任务 */
data class Task(
    val id: Long = 0,
    val title: String,
    val category: TaskCategory,
    /** 提醒时间（epoch millis），null = 不设提醒 */
    val remindAtMillis: Long? = null,
    /** 用户原文中的时间表达，用于确认核对 */
    val timeExpression: String? = null,
    val note: String? = null,
    /** 用户原始输入（语音转写或文字） */
    val rawInput: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** AI 解析产出的任务草稿（落库前的中间结构，含 UI 提示标记；可序列化以随聊天记录持久化） */
@Serializable
data class TaskDraft(
    val title: String,
    val category: TaskCategory,
    val remindAtMillis: Long? = null,
    val timeExpression: String? = null,
    val note: String? = null,
    val rawInput: String = "",
    /** 解析时间早于当前被自动顺延到次日，需在 UI 高亮提示 */
    val adjustedToNextDay: Boolean = false,
    /** 由离线规则引擎解析（非 AI），需在 UI 标注 */
    val fromOfflineFallback: Boolean = false,
) {
    fun toTask(): Task = Task(
        title = title,
        category = category,
        remindAtMillis = remindAtMillis,
        timeExpression = timeExpression,
        note = note,
        rawInput = rawInput,
    )
}

/** 已自动落库的任务创建结果（卡片展示 + 撤销依据） */
data class CreatedTask(
    val draft: TaskDraft,
    val taskId: Long,
)
