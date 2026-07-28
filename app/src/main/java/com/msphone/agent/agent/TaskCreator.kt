package com.msphone.agent.agent

import com.msphone.agent.domain.model.CreatedTask
import com.msphone.agent.domain.model.TaskDraft
import com.msphone.agent.domain.repository.TaskRepository
import com.msphone.agent.reminder.ReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务落库执行器：插入数据库并注册提醒闹钟。
 * create_task 工具与离线降级通道共用，保证"创建即生效"。
 */
@Singleton
class TaskCreator @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) {

    suspend fun create(draft: TaskDraft): CreatedTask {
        val taskId = repository.insert(draft.toTask())
        draft.remindAtMillis?.let { scheduler.schedule(taskId, draft.title, it) }
        return CreatedTask(draft, taskId)
    }

    /** 撤销刚创建的任务：删除记录并取消闹钟 */
    suspend fun undo(taskId: Long) {
        scheduler.cancel(taskId)
        repository.delete(taskId)
    }
}

/** 单轮会话上下文：把当前用户原文传递给工具（记录到任务 rawInput 字段） */
@Singleton
class AgentContext @Inject constructor() {
    @Volatile
    var currentRawInput: String = ""
}
