package com.msphone.agent.agent.harness

import android.util.Log
import com.msphone.agent.agent.llm.FunctionSpec
import com.msphone.agent.agent.llm.ToolCall
import com.msphone.agent.agent.llm.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tool Calling Harness 核心：工具注册、参数校验与分发执行。
 */
class ToolRegistry {

    private val tools = linkedMapOf<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    /** 转换为 GLM API 的 tools 字段 */
    fun toToolDefinitions(): List<ToolDefinition> = tools.values.map {
        ToolDefinition(function = FunctionSpec(it.name, it.description, it.parametersSchema))
    }

    /** 校验参数 → 路由 → 执行；任何异常都转为 Failure 回传模型自纠错 */
    suspend fun dispatch(call: ToolCall): ToolResult {
        val tool = tools[call.function.name]
            ?: return ToolResult.Failure("未知工具: ${call.function.name}")

        val args = try {
            Json.parseToJsonElement(call.function.arguments.ifBlank { "{}" }).jsonObject
        } catch (e: Exception) {
            return ToolResult.Failure("arguments 不是合法 JSON: ${e.message}")
        }

        validateRequired(tool.parametersSchema, args)?.let { return ToolResult.Failure(it) }

        val startAt = System.currentTimeMillis()
        val result = try {
            tool.execute(args)
        } catch (e: Exception) {
            ToolResult.Failure("工具执行异常: ${e.message}")
        }
        Log.d(TAG, "tool=${tool.name} args=$args cost=${System.currentTimeMillis() - startAt}ms result=$result")
        return result
    }

    /** 基于 Schema required 字段的轻量校验 */
    private fun validateRequired(schema: JsonObject, args: JsonObject): String? {
        val required = schema["required"] as? JsonArray ?: return null
        val missing = required
            .mapNotNull { it.jsonPrimitive.contentOrNull }
            .filter { key -> args[key] == null || args[key] is JsonNull }
        return if (missing.isEmpty()) null else "缺少必填参数: ${missing.joinToString()}"
    }

    private companion object {
        const val TAG = "ToolRegistry"
    }
}
