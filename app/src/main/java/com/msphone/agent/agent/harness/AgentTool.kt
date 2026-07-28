package com.msphone.agent.agent.harness

import com.msphone.agent.domain.model.CreatedTask
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Agent 工具接口：新增能力只需实现本接口并注册到 [ToolRegistry]。
 */
interface AgentTool {
    /** 工具名，与发给模型的 tools 定义一致 */
    val name: String

    /** 供模型理解的功能描述 */
    val description: String

    /** JSON Schema 参数定义 */
    val parametersSchema: JsonObject

    suspend fun execute(args: JsonObject): ToolResult
}

/** 工具执行结果 */
sealed class ToolResult {

    /** 回传给模型的 JSON 字符串（role=tool 消息内容） */
    abstract val modelPayload: String

    data class Success(
        override val modelPayload: String,
        /** create_task 已落库的创建结果，由 TaskAgent 收集后交 UI 展示卡片 */
        val created: CreatedTask? = null,
    ) : ToolResult()

    data class Failure(val error: String) : ToolResult() {
        override val modelPayload: String
            get() = buildJsonObject { put("error", error) }.toString()
    }
}
