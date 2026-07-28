package com.msphone.agent.agent

import com.msphone.agent.agent.harness.ToolRegistry
import com.msphone.agent.agent.harness.ToolResult
import com.msphone.agent.agent.llm.ChatMessage
import com.msphone.agent.agent.llm.LlmClient
import com.msphone.agent.agent.parser.KeywordClassifier
import com.msphone.agent.agent.parser.LocalTimeParser
import com.msphone.agent.domain.model.AiHistoryRecord
import com.msphone.agent.domain.model.AiResultType
import com.msphone.agent.domain.model.AiSourceType
import com.msphone.agent.domain.model.ChatEntry
import com.msphone.agent.domain.model.ChatEntryType
import com.msphone.agent.domain.model.CreatedTask
import com.msphone.agent.domain.model.TaskDraft
import com.msphone.agent.domain.repository.AiHistoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Agent 处理结果 */
sealed class AgentReply {
    /** 任务已直接创建落库（可能多个），UI 展示卡片 + 撤销按钮 */
    data class TasksCreated(
        val created: List<CreatedTask>,
        /** 模型的收尾确认文本（可为空，由 UI 自行拼接） */
        val message: String? = null,
    ) : AgentReply()

    /** 纯文本回复（闲聊 / 查询结果播报） */
    data class Text(val content: String) : AgentReply()

    data class Error(val message: String) : AgentReply()
}

/**
 * Agent 编排器：组装 Prompt → 调 GLM → 经 Harness 分发工具 → 产出回复。
 * 网络失败时自动切换本地规则解析（离线降级通道）。
 */
@Singleton
class TaskAgent @Inject constructor(
    private val llm: LlmClient,
    private val registry: ToolRegistry,
    private val taskCreator: TaskCreator,
    private val agentContext: AgentContext,
    private val aiHistory: AiHistoryRepository,
) {

    /**
     * @param history 当前上下文窗口内的历史聊天记录（不含本次输入），
     *                取最近 [MAX_HISTORY_ENTRIES] 条随请求发送，让模型能理解"刚才那个"等指代。
     */
    suspend fun process(userText: String, history: List<ChatEntry> = emptyList()): AgentReply {
        val startAt = System.currentTimeMillis()
        var sourceType = AiSourceType.LLM
        val reply = try {
            agentContext.currentRawInput = userText
            runLlmLoop(userText, history)
        } catch (e: IOException) {
            sourceType = AiSourceType.OFFLINE_FALLBACK
            offlineFallback(userText)
        } catch (e: HttpException) {
            sourceType = AiSourceType.OFFLINE_FALLBACK
            offlineFallback(userText)
        } catch (e: Exception) {
            AgentReply.Error("AI 解析失败：${e.message ?: "未知错误"}")
        }
        // 防幻觉纠错路径内部也可能触发离线降级，以草稿标记为准
        if (reply is AgentReply.TasksCreated && reply.created.any { it.draft.fromOfflineFallback }) {
            sourceType = AiSourceType.OFFLINE_FALLBACK
        }
        recordHistory(userText, sourceType, reply, System.currentTimeMillis() - startAt)
        return reply
    }

    /** 解析历史埋点：在线/离线/错误三条路径的汇聚点，写入失败不影响主流程 */
    private suspend fun recordHistory(
        rawInput: String,
        sourceType: AiSourceType,
        reply: AgentReply,
        costMillis: Long,
    ) {
        val (resultType, summary) = when (reply) {
            is AgentReply.TasksCreated -> AiResultType.TASKS_CREATED to
                reply.created.joinToString("；") { c ->
                    "task_id=${c.taskId}「${c.draft.title}」${c.draft.category.name}"
                }
            is AgentReply.Text -> AiResultType.TEXT_REPLY to reply.content
            is AgentReply.Error -> AiResultType.ERROR to reply.message
        }
        aiHistory.record(
            AiHistoryRecord(
                rawInput = rawInput,
                sourceType = sourceType,
                resultType = resultType,
                resultSummary = summary,
                costMillis = costMillis,
            )
        )
    }

    private suspend fun runLlmLoop(userText: String, history: List<ChatEntry>): AgentReply {
        val messages = mutableListOf(ChatMessage.system(buildSystemPrompt()))
        messages += history.takeLast(MAX_HISTORY_ENTRIES).mapNotNull { it.toChatMessage() }
        messages += ChatMessage.user(userText)

        val created = mutableListOf<CreatedTask>()
        var hallucinationCorrected = false
        var formatCorrected = false
        var anyToolCalled = false

        // 模型→工具→模型 循环，最大 MAX_TOOL_ROUNDS 轮防止死循环
        repeat(MAX_TOOL_ROUNDS) {
            val assistant = llm.chat(messages, registry.toToolDefinitions())
            messages += assistant

            val toolCalls = assistant.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                val text = assistant.content?.takeIf { it.isNotBlank() }
                    ?: "我没有理解你的意思，可以换个说法试试。"
                // 已有任务落库：此文本就是收尾确认
                if (created.isNotEmpty()) return AgentReply.TasksCreated(created, text)
                // 工具调用泄漏到正文：没有 tool_calls 却在文本里写工具名/JSON 参数，
                // 说明模型想调用但格式错了 → 要求它只输出工具调用重试一次
                if (created.isEmpty() && !formatCorrected && leakedToolCallRegex.containsMatchIn(text)) {
                    formatCorrected = true
                    messages += ChatMessage.user(
                        "你把工具调用写进了正文，这样不会被执行。" +
                            "请重新发起：本条回复只包含标准格式的工具调用，不要输出任何自然语言或 JSON 文本。"
                    )
                    return@repeat
                }
                // 防幻觉：模型声称已执行操作但本轮对话从未调用任何工具 → 强制纠错一次，再犯则离线兼底
                if (!anyToolCalled && hallucinationRegex.containsMatchIn(text)) {
                    if (hallucinationCorrected) return offlineFallback(userText)
                    hallucinationCorrected = true
                    messages += ChatMessage.user(
                        "你刚才并没有调用任何工具，操作实际上没有执行。" +
                            "请立即调用对应工具完成操作（创建用 create_task，改标题/分类用 update_task，" +
                            "删除用 delete_task），不要只用文字回复；如果做不到，如实告知用户。"
                    )
                    return@repeat
                }
                return AgentReply.Text(text)
            }

            // 同轮多个工具调用并发执行（dispatch 已把异常转为 Failure，单个失败不会取消兄弟协程），
            // 结果按原顺序回填，保证每个 tool_call_id 都有对应的 role=tool 消息
            val results = coroutineScope {
                toolCalls.map { call -> async { call to registry.dispatch(call) } }.awaitAll()
            }
            anyToolCalled = true
            for ((call, result) in results) {
                if (result is ToolResult.Success && result.created != null) {
                    created += result.created
                }
                messages += ChatMessage.tool(call.id, result.modelPayload)
            }
        }
        // 轮数耗尽：已创建的任务照常返回，不丢结果
        if (created.isNotEmpty()) return AgentReply.TasksCreated(created)
        return AgentReply.Error("模型工具调用超过最大轮数（$MAX_TOOL_ROUNDS），请重试")
    }

    private fun buildSystemPrompt(): String {
        val now = ZonedDateTime.now()
        val week = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
        return """
            你是任务管理助手，负责把用户的自然语言转成结构化任务。
            当前时间：${now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}（$week），所有相对时间以此为基准换算。
            规则：
            1. 用户描述任务、待办、日程或提醒事项时，必须调用 create_task 工具。严禁在未调用工具的情况下声称任务已创建；只有工具返回 task_created 后才算创建成功。
            2. 用户一句话包含多个任务时（如"明天开会和后天送货"），必须为每个任务分别调用一次 create_task，不得合并或遗漏。
            3. 分类标准：WORK=会议、汇报、项目、客户、加班、面试、评审等工作事务；LIFE=购物、家务、就医、社交、健身、缴费等生活事务；难以判断时默认 LIFE。
            4. remind_time 使用 ISO8601 格式并带时区偏移。只有日期没有时刻时默认 09:00；"早上"默认 08:00、"中午"默认 12:00、"下午"默认 15:00、"晚上"默认 20:00。
            5. 用户未提及任何时间时，不要编造 remind_time。
            6. 查询任务用 query_tasks，标记完成用 complete_task，修改提醒时间用 set_reminder，改标题/分类/备注用 update_task，删除任务用 delete_task。用户要求删除/清空多个任务时，先 query_tasks 拿到 id，再把所有 id 一次性传给 delete_task 的 task_ids 批量删除，不要逐个调用。
            7. 诚实原则：任何"已创建/已修改/已删除/已设置"的表述，都必须以对应工具的成功返回为前提；工具返回失败或能力范围外的请求（如导出数据、设重复提醒），如实告知做不到或失败原因，严禁假装完成。
            8. 主动性边界：用户指令明确时直接执行，不要反问确认；信息确实缺失时（如"把那个任务改一下"但历史中无法定位是哪个）才简短追问一句；不要自作主张创建用户没提到的任务或修改用户没要求改的字段。
            9. 输出纪律：决定调用工具时，那条回复必须只包含工具调用本身，正文留空；严禁在同一条回复里夹带自然语言、工具名或 JSON 参数文本。面向用户的确认话语，等所有工具执行完毕后再单独用一条纯文本回复输出。
            10. 工具全部调用完成后，用一句简短中文确认结果；与任务无关的闲聊直接简短回复，不调用工具。
            11. 对话历史中【已创建任务】标记的是真实存在的任务（含 task_id）。用户说"刚才那个/上一个/把它改成…"时，优先从历史中定位对应 task_id 再调用对应工具；标注（已撤销）的任务已不存在，不要引用。
        """.trimIndent()
    }

    /** 离线降级：本地规则时间解析 + 关键词分类，同样直接落库 */
    private suspend fun offlineFallback(userText: String): AgentReply {
        val parsed = LocalTimeParser.parse(userText)
        val title = parsed
            ?.let { userText.replace(it.expression, "") }
            .orEmpty()
            .ifBlank { userText }
            .replace(leadingVerbRegex, "")
            .trim(' ', '，', ',', '。', '、', '；')
            .ifBlank { userText }
        val created = taskCreator.create(
            TaskDraft(
                title = title,
                category = KeywordClassifier.classify(userText),
                remindAtMillis = parsed?.timeMillis,
                timeExpression = parsed?.expression,
                rawInput = userText,
                fromOfflineFallback = true,
            )
        )
        return AgentReply.TasksCreated(listOf(created))
    }

    /** 把持久化的聊天记录压缩成模型可读的历史消息 */
    private fun ChatEntry.toChatMessage(): ChatMessage? = when (type) {
        ChatEntryType.USER -> ChatMessage.user(content)
        // 错误消息不进上下文，避免模型模仿错误口径
        ChatEntryType.ASSISTANT -> if (isError) null else ChatMessage.assistant(content)
        ChatEntryType.TASK_CARD -> draft?.let { d ->
            val time = d.remindAtMillis?.let { "，提醒时间 ${java.time.Instant.ofEpochMilli(it)
                .atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}" } ?: "，无提醒"
            val state = if (undone) "（已撤销）" else ""
            ChatMessage.assistant("【已创建任务】task_id=$taskId，标题「${d.title}」，分类 ${d.category.name}$time$state")
        }
        ChatEntryType.DIVIDER -> null
    }

    private companion object {
        const val MAX_TOOL_ROUNDS = 10

        /** 随请求携带的最大历史条数（滑动窗口） */
        const val MAX_HISTORY_ENTRIES = 20

        val leadingVerbRegex = Regex("^(记得|提醒我|帮我记|帮我)")

        /** 模型未调工具却声称已完成操作的典型幻觉句式（创建/修改/删除全覆盖） */
        val hallucinationRegex =
            Regex("已(经)?(为您|为你|帮您|帮你)?(成功)?(创建|添加|设置|记录|安排|设定|修改|更新|更改|重命名|改名|删除|移除|清空|标记)")

        /** 工具调用泄漏到正文的特征：出现工具名或 JSON 对象开头（模型想调用但格式错误） */
        val leakedToolCallRegex =
            Regex("create_task|query_tasks|complete_task|set_reminder|delete_task|update_task|\\{\\s*\"")
    }
}
