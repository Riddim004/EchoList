package com.msphone.agent.domain.model

/** 聊天记录条目类型 */
enum class ChatEntryType {
    /** 用户输入 */
    USER,

    /** 助手文本回复 */
    ASSISTANT,

    /** 任务创建结果卡片 */
    TASK_CARD,

    /** 上下文分隔线（清空上下文后插入，Agent 只读取其后的历史） */
    DIVIDER,
}

/** 聊天记录（持久化到 Room，UI 展示与模型上下文共用） */
data class ChatEntry(
    val id: Long = 0,
    val type: ChatEntryType,
    /** USER/ASSISTANT 的文本内容，其余类型为空串 */
    val content: String = "",
    val isError: Boolean = false,
    /** TASK_CARD：关联的任务 id */
    val taskId: Long? = null,
    /** TASK_CARD：卡片展示用的任务草稿 */
    val draft: TaskDraft? = null,
    /** TASK_CARD：是否已被撤销 */
    val undone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
