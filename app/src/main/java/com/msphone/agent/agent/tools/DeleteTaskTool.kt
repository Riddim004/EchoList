package com.msphone.agent.agent.tools

import com.msphone.agent.agent.harness.AgentTool
import com.msphone.agent.agent.harness.ToolResult
import com.msphone.agent.domain.repository.TaskRepository
import com.msphone.agent.reminder.ReminderScheduler
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/** 删除任务（支持批量），并取消对应提醒闹钟 */
class DeleteTaskTool @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) : AgentTool {

    override val name = "delete_task"

    override val description =
        "删除一个或多个任务（彻底移除，不同于标记完成）。当用户要求删除/清空任务时调用；" +
            "task_ids 支持批量传入多个 id，删除多个任务时应一次调用完成；id 可先通过 query_tasks 查询获得。"

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("task_ids") {
                put("type", "array")
                put("description", "要删除的任务 id 列表，支持一次传多个")
                putJsonObject("items") { put("type", "integer") }
            }
        }
        putJsonArray("required") { add("task_ids") }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val ids = parseIds(args["task_ids"])
        if (ids.isEmpty()) return ToolResult.Failure("task_ids 必须是非空的整数数组")

        val deleted = mutableListOf<Long>()
        val notFound = mutableListOf<Long>()
        for (id in ids) {
            if (repository.getById(id) == null) {
                notFound += id
                continue
            }
            scheduler.cancel(id)
            repository.delete(id)
            deleted += id
        }
        if (deleted.isEmpty()) return ToolResult.Failure("任务不存在: task_ids=$notFound")

        val payload = buildJsonObject {
            put("status", "deleted")
            putJsonArray("deleted_ids") { deleted.forEach { add(it) } }
            if (notFound.isNotEmpty()) {
                putJsonArray("not_found_ids") { notFound.forEach { add(it) } }
            }
        }.toString()
        return ToolResult.Success(payload)
    }

    /** 兼容模型可能传出的三种形态：数组、单个数字、逗号分隔字符串 */
    private fun parseIds(element: kotlinx.serialization.json.JsonElement?): List<Long> = when (element) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.let { p -> p.longOrNull ?: p.contentOrNull?.toLongOrNull() } }
        is JsonPrimitive -> element.contentOrNull
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()
        else -> emptyList()
    }.distinct()
}
