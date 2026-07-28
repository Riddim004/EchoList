package com.msphone.agent.agent.tools

import com.msphone.agent.agent.harness.AgentTool
import com.msphone.agent.agent.harness.ToolResult
import com.msphone.agent.domain.model.TaskCategory
import com.msphone.agent.domain.repository.TaskRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** 查询任务：支持按类别 / 日期过滤，供复合指令的多轮工具调用使用 */
class QueryTasksTool @Inject constructor(
    private val repository: TaskRepository,
) : AgentTool {

    override val name = "query_tasks"

    override val description =
        "查询已有任务列表。当用户想查看、询问自己的任务或日程安排时调用。"

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("category") {
                put("type", "string")
                putJsonArray("enum") { add("WORK"); add("LIFE") }
                put("description", "按类别过滤，不传则查全部")
            }
            putJsonObject("date") {
                put("type", "string")
                put("description", "按日期过滤，格式 YYYY-MM-DD，不传则查全部")
            }
        }
        putJsonArray("required") { }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val category = args.stringArg("category")?.let { value ->
            TaskCategory.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
        val date = args.stringArg("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val zone = ZoneId.systemDefault()
        val fromMillis = date?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val toMillis = date?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.minus(1)

        val tasks = repository.query(category, fromMillis, toMillis)
        val payload = buildJsonObject {
            put("count", tasks.size)
            putJsonArray("tasks") {
                tasks.take(MAX_RESULT).forEach { task ->
                    addJsonObject {
                        put("task_id", task.id)
                        put("title", task.title)
                        put("category", task.category.name)
                        put("remind_time", task.remindAtMillis?.let { formatMillis(it) } ?: "无提醒")
                        put("status", task.status.name)
                    }
                }
            }
        }.toString()
        return ToolResult.Success(payload)
    }

    private companion object {
        const val MAX_RESULT = 20
    }
}
