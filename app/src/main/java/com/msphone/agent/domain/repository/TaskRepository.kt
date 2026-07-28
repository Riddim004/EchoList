package com.msphone.agent.domain.repository

import com.msphone.agent.domain.model.Task
import com.msphone.agent.domain.model.TaskCategory
import com.msphone.agent.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

interface TaskRepository {

    /** 订阅全部任务（按提醒时间/创建时间排序） */
    fun observeTasks(): Flow<List<Task>>

    suspend fun getById(id: Long): Task?

    /** 未完成且设置了提醒时间的任务（用于闹钟对账/开机恢复） */
    suspend fun getPendingWithReminder(): List<Task>

    /** 按类别与时间范围查询（供 query_tasks 工具使用） */
    suspend fun query(
        category: TaskCategory? = null,
        fromMillis: Long? = null,
        toMillis: Long? = null,
    ): List<Task>

    /** @return 新任务 id */
    suspend fun insert(task: Task): Long

    suspend fun update(task: Task)

    suspend fun updateStatus(id: Long, status: TaskStatus)

    suspend fun updateRemindTime(id: Long, remindAtMillis: Long?)

    suspend fun delete(id: Long)
}
