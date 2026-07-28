package com.msphone.agent.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msphone.agent.domain.model.Task
import com.msphone.agent.domain.model.TaskCategory
import com.msphone.agent.domain.model.TaskStatus
import com.msphone.agent.domain.repository.TaskRepository
import com.msphone.agent.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    enum class Filter(val label: String) {
        ALL("全部"), WORK("工作"), LIFE("生活"), DONE("已完成")
    }

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter = _filter.asStateFlow()

    /** 最近删除的任务，用于撤销 */
    private var lastDeleted: Task? = null

    val tasks: StateFlow<List<Task>> =
        combine(repository.observeTasks(), _filter) { list, filter ->
            when (filter) {
                Filter.ALL -> list.filter { it.status != TaskStatus.DONE }
                Filter.WORK -> list.filter { it.category == TaskCategory.WORK && it.status != TaskStatus.DONE }
                Filter.LIFE -> list.filter { it.category == TaskCategory.LIFE && it.status != TaskStatus.DONE }
                Filter.DONE -> list.filter { it.status == TaskStatus.DONE }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 各筛选项的待办数量（已完成不计数），筛选器括号展示用 */
    val counts: StateFlow<Map<Filter, Int>> =
        repository.observeTasks().map { list ->
            val pending = list.filter { it.status != TaskStatus.DONE }
            mapOf(
                Filter.ALL to pending.size,
                Filter.WORK to pending.count { it.category == TaskCategory.WORK },
                Filter.LIFE to pending.count { it.category == TaskCategory.LIFE },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setFilter(filter: Filter) {
        _filter.value = filter
    }

    fun complete(task: Task) {
        viewModelScope.launch {
            repository.updateStatus(task.id, TaskStatus.DONE)
            scheduler.cancel(task.id)
        }
    }

    fun reopen(task: Task) {
        viewModelScope.launch {
            repository.updateStatus(task.id, TaskStatus.PENDING)
            task.remindAtMillis?.let { remindAt ->
                if (remindAt > System.currentTimeMillis()) {
                    scheduler.schedule(task.id, task.title, remindAt)
                }
            }
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            lastDeleted = task
            repository.delete(task.id)
            scheduler.cancel(task.id)
        }
    }

    /** 撤销最近一次删除 */
    fun undoDelete() {
        val task = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            val newId = repository.insert(task.copy(id = 0))
            task.remindAtMillis?.let { remindAt ->
                if (remindAt > System.currentTimeMillis() && task.status == TaskStatus.PENDING) {
                    scheduler.schedule(newId, task.title, remindAt)
                }
            }
        }
    }
}

