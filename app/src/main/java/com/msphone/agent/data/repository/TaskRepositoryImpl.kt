package com.msphone.agent.data.repository

import com.msphone.agent.data.local.TaskDao
import com.msphone.agent.data.local.TaskEntity
import com.msphone.agent.domain.model.Task
import com.msphone.agent.domain.model.TaskCategory
import com.msphone.agent.domain.model.TaskStatus
import com.msphone.agent.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
) : TaskRepository {

    override fun observeTasks(): Flow<List<Task>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Task? = dao.getById(id)?.toDomain()

    override suspend fun getPendingWithReminder(): List<Task> =
        dao.getPendingWithReminder().map { it.toDomain() }

    override suspend fun query(category: TaskCategory?, fromMillis: Long?, toMillis: Long?): List<Task> =
        dao.query(category?.name, fromMillis, toMillis).map { it.toDomain() }

    override suspend fun insert(task: Task): Long = dao.insert(task.toEntity())

    override suspend fun update(task: Task) =
        dao.update(task.copy(updatedAt = System.currentTimeMillis()).toEntity())

    override suspend fun updateStatus(id: Long, status: TaskStatus) =
        dao.updateStatus(id, status.name)

    override suspend fun updateRemindTime(id: Long, remindAtMillis: Long?) =
        dao.updateRemindTime(id, remindAtMillis)

    override suspend fun delete(id: Long) = dao.deleteById(id)
}

private fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    category = TaskCategory.from(category),
    remindAtMillis = remindTimeMillis,
    timeExpression = timeExpression,
    note = note,
    rawInput = rawInput,
    status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.PENDING),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    category = category.name,
    remindTimeMillis = remindAtMillis,
    timeExpression = timeExpression,
    note = note,
    rawInput = rawInput,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
