package com.msphone.agent.agent.tools

import com.msphone.agent.agent.harness.AgentTool
import com.msphone.agent.agent.harness.ToolResult
import com.msphone.agent.domain.repository.TaskRepository
import com.msphone.agent.reminder.ReminderScheduler
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/** 为已有任务设置 / 修改提醒时间 */
class SetReminderTool @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) : AgentTool {

    override val name = "set_reminder"

    override val description =
        "为已存在的任务设置或修改提醒时间。当用户想调整某个任务的提醒时调用；task_id 可先通过 query_tasks 查询获得。"

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("task_id") {
                put("type", "integer")
                put("description", "任务 id")
            }
            putJsonObject("remind_time") {
                put("type", "string")
                put("description", "ISO8601 提醒时间，必须晚于当前时间")
            }
        }
        putJsonArray("required") { add("task_id"); add("remind_time") }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val taskId = args.longArg("task_id")
            ?: return ToolResult.Failure("task_id 必须是整数")
        val remindTimeRaw = args.stringArg("remind_time")
            ?: return ToolResult.Failure("remind_time 不能为空")
        val remindAtMillis = parseIsoToMillis(remindTimeRaw)
            ?: return ToolResult.Failure("remind_time 不是合法的 ISO8601 时间: $remindTimeRaw")
        if (remindAtMillis <= System.currentTimeMillis()) {
            return ToolResult.Failure("提醒时间必须晚于当前时间")
        }
        val task = repository.getById(taskId)
            ?: return ToolResult.Failure("任务不存在: task_id=$taskId")

        repository.updateRemindTime(taskId, remindAtMillis)
        scheduler.schedule(taskId, task.title, remindAtMillis)
        val payload = buildJsonObject {
            put("status", "reminder_updated")
            put("task_id", taskId)
            put("remind_time", formatMillis(remindAtMillis))
        }.toString()
        return ToolResult.Success(payload)
    }
}
