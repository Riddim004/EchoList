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
        var model = BuildConfig.GLM_MODEL
        var rateLimitRetries = 0
        var timeoutRetries = 0
        while (true) {
            try {
                val response = api.chat(
                    ChatRequest(
                        model = model,
                        messages = messages,
                        temperature = 0.1,
                        tools = tools.takeIf { it.isNotEmpty() },
                        toolChoice = if (tools.isNotEmpty()) "auto" else null,
                        // 实测结论（v0.15.0 横评）：旗舰 glm-5.2 关思考仍全对且延迟减半（~5.6s vs 8.7s）；
                        // 降级的 4.7-flash 底子弱，保留思考撑准确率
                        thinking = Thinking(
                            type = if (model == BuildConfig.GLM_FALLBACK_MODEL) "enabled" else "disabled"
                        ),
                    )
                )
                return response.choices.firstOrNull()?.message
                    ?: throw IOException("GLM 返回结果为空")
            } catch (e: HttpException) {
                if (e.code() == 429 && rateLimitRetries < MAX_RATE_LIMIT_RETRIES) {
                    // 429 限流：指数退避重试（1s / 2s）
                    delay(1000L shl rateLimitRetries)
                    rateLimitRetries++
                } else if (model != BuildConfig.GLM_FALLBACK_MODEL) {
                    // 旗舰资源包耗尽/计费失败/限流重试无效 → 自动降级到免费模型，不断服不产费
                    model = BuildConfig.GLM_FALLBACK_MODEL
                    rateLimitRetries = 0
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
