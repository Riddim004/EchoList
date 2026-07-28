package com.msphone.agent.agent.tools

import com.msphone.agent.agent.harness.AgentTool
import com.msphone.agent.agent.harness.ToolResult
import com.msphone.agent.domain.model.TaskCategory
import com.msphone.agent.domain.model.TaskStatus
import com.msphone.agent.domain.repository.TaskRepository
import com.msphone.agent.reminder.ReminderScheduler
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/** 修改已有任务的标题 / 分类 / 备注（提醒时间请用 set_reminder） */
class UpdateTaskTool @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) : AgentTool {

    override val name = "update_task"

    override val description =
        "修改已存在任务的标题、分类或备注。当用户要求改名/重命名任务、调整工作生活分类、补充备注时调用；" +
            "task_id 可先通过 query_tasks 查询获得。修改提醒时间请改用 set_reminder。"

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("task_id") {
                put("type", "integer")
                put("description", "任务 id")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "新标题（不改则省略）")
            }
            putJsonObject("category") {
                put("type", "string")
                putJsonArray("enum") { add("WORK"); add("LIFE") }
                put("description", "新分类（不改则省略）")
            }
            putJsonObject("note") {
                put("type", "string")
                put("description", "新备注（不改则省略）")
            }
        }
        putJsonArray("required") { add("task_id") }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val taskId = args.longArg("task_id")
            ?: return ToolResult.Failure("task_id 必须是整数")
        val task = repository.getById(taskId)
            ?: return ToolResult.Failure("任务不存在: task_id=$taskId")

        val newTitle = args.stringArg("title")
        val newCategory = args.stringArg("category")?.let { TaskCategory.from(it) }
        val newNote = args.stringArg("note")
        if (newTitle == null && newCategory == null && newNote == null) {
            return ToolResult.Failure("title/category/note 至少提供一项")
        }

        val updated = task.copy(
            title = newTitle ?: task.title,
            category = newCategory ?: task.category,
            note = newNote ?: task.note,
            updatedAt = System.currentTimeMillis(),
        )
        repository.update(updated)

        // 标题变更时重新注册闹钟，保证提醒通知显示新标题
        if (newTitle != null && updated.status == TaskStatus.PENDING) {
            updated.remindAtMillis?.let { remindAt ->
                if (remindAt > System.currentTimeMillis()) {
                    scheduler.schedule(taskId, updated.title, remindAt)
                }
            }
        }

        val payload = buildJsonObject {
            put("status", "updated")
            put("task_id", taskId)
            put("title", updated.title)
            put("category", updated.category.name)
            updated.note?.let { put("note", it) }
        }.toString()
        return ToolResult.Success(payload)
    }
}
