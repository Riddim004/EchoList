package com.msphone.agent.agent.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** GLM Chat Completions 接口的消息结构 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
) {
    companion object {
        fun system(content: String) = ChatMessage(role = "system", content = content)
        fun user(content: String) = ChatMessage(role = "user", content = content)
        fun assistant(content: String) = ChatMessage(role = "assistant", content = content)
        fun tool(toolCallId: String, content: String) =
            ChatMessage(role = "tool", content = content, toolCallId = toolCallId)
    }
}

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall,
)

@Serializable
data class FunctionCall(
    val name: String,
    /** 模型输出的 JSON 字符串参数 */
    val arguments: String,
)

/** tools 字段中的工具定义 */
@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionSpec,
)

@Serializable
data class FunctionSpec(
    val name: String,
    val description: String,
    /** JSON Schema */
    val parameters: JsonObject,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.1,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)
