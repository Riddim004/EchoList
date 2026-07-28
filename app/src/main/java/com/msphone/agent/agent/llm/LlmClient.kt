package com.msphone.agent.agent.llm

import com.msphone.agent.BuildConfig
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM 客户端抽象：便于后续替换模型供应商。
 */
interface LlmClient {
    /**
     * 发送一轮对话，返回 assistant 消息（可能包含 tool_calls）。
     * @throws IOException 网络不可用/超时（由上层触发离线降级）
     */
    suspend fun chat(messages: List<ChatMessage>, tools: List<ToolDefinition>): ChatMessage
}

@Singleton
class GlmLlmClient @Inject constructor(
    private val api: GlmApi,
) : LlmClient {

    override suspend fun chat(messages: List<ChatMessage>, tools: List<ToolDefinition>): ChatMessage {
        var rateLimitRetries = 0
        var timeoutRetries = 0
        while (true) {
            try {
                val response = api.chat(
                    ChatRequest(
                        model = BuildConfig.GLM_MODEL,
                        messages = messages,
                        temperature = 0.1,
                        tools = tools.takeIf { it.isNotEmpty() },
                        toolChoice = if (tools.isNotEmpty()) "auto" else null,
                    )
                )
                return response.choices.firstOrNull()?.message
                    ?: throw IOException("GLM 返回结果为空")
            } catch (e: HttpException) {
                // 429 限流：指数退避重试（1s / 2s）
                if (e.code() == 429 && rateLimitRetries < MAX_RATE_LIMIT_RETRIES) {
                    delay(1000L shl rateLimitRetries)
                    rateLimitRetries++
                } else {
                    throw e
                }
            } catch (e: SocketTimeoutException) {
                // 超时重试 1 次
                if (timeoutRetries < MAX_TIMEOUT_RETRIES) timeoutRetries++ else throw e
            }
        }
    }

    private companion object {
        const val MAX_RATE_LIMIT_RETRIES = 2
        const val MAX_TIMEOUT_RETRIES = 1
    }
}
