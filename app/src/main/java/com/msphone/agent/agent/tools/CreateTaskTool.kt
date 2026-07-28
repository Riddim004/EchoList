package com.msphone.agent.agent.tools

import com.msphone.agent.agent.AgentContext
import com.msphone.agent.agent.TaskCreator
import com.msphone.agent.agent.harness.AgentTool
import com.msphone.agent.agent.harness.ToolResult
import com.msphone.agent.domain.model.TaskCategory
import com.msphone.agent.domain.model.TaskDraft
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 主工具：把模型解析出的字段直接落库并注册提醒（创建即生效，用户可在卡片上撤销）。
 */
class CreateTaskTool @Inject constructor(
    private val taskCreator: TaskCreator,
    private val agentContext: AgentContext,
) : AgentTool {

    override val name = "create_task"

    override val description =
        "创建一个任务并直接保存到任务清单（含提醒闹钟）。当用户想添加任务、待办、日程或提醒事项时必须调用本工具；" +
            "若用户一次描述了多个任务，必须对每个任务分别调用一次本工具。"

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "精炼的任务标题，如'产品评审会'")
            }
            putJsonObject("category") {
                put("type", "string")
                putJsonArray("enum") { add("WORK"); add("LIFE") }
                put("description", "任务类别：WORK=工作，LIFE=生活")
            }
            putJsonObject("remind_time") {
                put("type", "string")
                put("description", "ISO8601 提醒时间，如 2026-07-28T15:00:00+08:00；用户未提及时间则不传")
            }
            putJsonObject("time_expression") {
                put("type", "string")
                put("description", "用户原文中的时间表达，如'明天下午三点'")
            }
            putJsonObject("note") {
                put("type", "string")
                put("description", "补充信息")
            }
        }
        putJsonArray("required") { add("title"); add("category") }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val title = args.stringArg("title")
            ?: return ToolResult.Failure("title 不能为空")
        val category = TaskCategory.from(args.stringArg("category"))
        val remindTimeRaw = args.stringArg("remind_time")

        var adjustedToNextDay = false
        var remindAtMillis: Long? = null
        if (remindTimeRaw != null) {
            val parsed = parseIsoToMillis(remindTimeRaw)
                ?: return ToolResult.Failure("remind_time 不是合法的 ISO8601 时间: $remindTimeRaw")
            val now = System.currentTimeMillis()
            remindAtMillis = when {
                parsed >= now -> parsed
                // 已过时但在 24h 内：顺延到次日同刻（UI 高亮提示）
                now - parsed <= TimeUnit.DAYS.toMillis(1) -> {
                    adjustedToNextDay = true
                    parsed + TimeUnit.DAYS.toMillis(1)
                }
                // 早于当前超过 24h：判为解析异常
                else -> return ToolResult.Failure("解析出的提醒时间早于当前时间超过24小时，请提示用户手动确认时间")
            }
        }

        val draft = TaskDraft(
            title = title,
            category = category,
            remindAtMillis = remindAtMillis,
            timeExpression = args.stringArg("time_expression"),
            note = args.stringArg("note"),
            rawInput = agentContext.currentRawInput,
            adjustedToNextDay = adjustedToNextDay,
        )
        // 直接落库 + 注册闹钟，创建即生效
        val created = taskCreator.create(draft)
        val payload = buildJsonObject {
            put("status", "task_created")
            put("task_id", created.taskId)
            put("title", draft.title)
            put("category", draft.category.name)
            put("remind_time", draft.remindAtMillis?.let { formatMillis(it) } ?: "无提醒")
        }.toString()
        return ToolResult.Success(payload, created)
    }
}

// ---------- 工具共用的参数与时间解析辅助 ----------

internal fun JsonObject.stringArg(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

internal fun JsonObject.longArg(key: String): Long? =
    (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

/** 宽容解析模型输出的时间字符串（带/不带时区、日期时间/纯日期、空格分隔） */
internal fun parseIsoToMillis(text: String): Long? {
    val zone = ZoneId.systemDefault()
    runCatching { return OffsetDateTime.parse(text).toInstant().toEpochMilli() }
    runCatching { return LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli() }
    runCatching {
        return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]"))
            .atZone(zone).toInstant().toEpochMilli()
    }
    runCatching { return LocalDate.parse(text).atTime(9, 0).atZone(zone).toInstant().toEpochMilli() }
    return null
}

internal fun formatMillis(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
