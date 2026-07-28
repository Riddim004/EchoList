package com.msphone.agent.agent.tools

import com.msphone.agent.agent.harness.AgentTool
import com.msphone.agent.agent.harness.ToolResult
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

/** 标记任务完成，并取消对应提醒闹钟 */
class CompleteTaskTool @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) : AgentTool {

    override val name = "complete_task"

    override val description =
        "把指定任务标记为已完成。当用户说某个任务已经做完时调用；task_id 可先通过 query_tasks 查询获得。"

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("task_id") {
                put("type", "integer")
                put("description", "任务 id")
            }
        }
        putJsonArray("required") { add("task_id") }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val taskId = args.longArg("task_id")
            ?: return ToolResult.Failure("task_id 必须是整数")
        repository.getById(taskId)
            ?: return ToolResult.Failure("任务不存在: task_id=$taskId")

        repository.updateStatus(taskId, TaskStatus.DONE)
        scheduler.cancel(taskId)
        val payload = buildJsonObject {
            put("status", "completed")
            put("task_id", taskId)
        }.toString()
        return ToolResult.Success(payload)
    }
}
