package com.msphone.agent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msphone.agent.agent.AgentReply
import com.msphone.agent.agent.TaskAgent
import com.msphone.agent.agent.TaskCreator
import com.msphone.agent.domain.model.ChatEntry
import com.msphone.agent.domain.model.ChatEntryType
import com.msphone.agent.ui.common.formatFriendlyTime
import com.msphone.agent.domain.model.CreatedTask
import com.msphone.agent.domain.model.TaskDraft
import com.msphone.agent.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agent: TaskAgent,
    private val taskCreator: TaskCreator,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    /** 聊天流中的一条内容（由持久化的 ChatEntry 映射而来） */
    sealed class ChatItem {
        abstract val id: Long

        data class User(override val id: Long, val text: String) : ChatItem()

        data class Assistant(
            override val id: Long,
            val text: String,
            val isError: Boolean = false,
        ) : ChatItem()

        /** 已创建任务的结果卡片（自动落库，按钮用于撤销） */
        data class TaskCard(
            override val id: Long,
            val draft: TaskDraft,
            val taskId: Long,
            val undone: Boolean = false,
        ) : ChatItem()

        /** 上下文分隔线：其后为新会话 */
        data class Divider(override val id: Long) : ChatItem()
    }

    data class UiState(
        val items: List<ChatItem> = emptyList(),
        val processing: Boolean = false,
    )

    private val processing = MutableStateFlow(false)

    val uiState: StateFlow<UiState> =
        combine(chatRepository.observeAll(), processing) { entries, busy ->
            val items = entries.mapNotNull { it.toChatItem() }
            UiState(
                items = items.ifEmpty { listOf(GREETING) },
                processing = busy,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UiState(items = listOf(GREETING)),
        )

    /** 提交任务描述，最长 500 字符 */
    fun send(text: String) {
        val trimmed = text.trim().take(MAX_INPUT_LENGTH)
        if (trimmed.isEmpty() || processing.value) return
        processing.value = true

        viewModelScope.launch {
            try {
                // 先取当前上下文窗口（不含本句），再落库本句
                val history = chatRepository.getCurrentContext()
                chatRepository.insert(ChatEntry(type = ChatEntryType.USER, content = trimmed))

                when (val reply = agent.process(trimmed, history)) {
                    is AgentReply.TasksCreated -> persistCreated(reply)
                    is AgentReply.Text -> chatRepository.insert(
                        ChatEntry(type = ChatEntryType.ASSISTANT, content = reply.content)
                    )
                    is AgentReply.Error -> chatRepository.insert(
                        ChatEntry(type = ChatEntryType.ASSISTANT, content = reply.message, isError = true)
                    )
                }
            } finally {
                processing.value = false
            }
        }
    }

    /** 用户点击卡片"撤销"：删除任务、取消闹钟并标记记录 */
    fun undoTask(itemId: Long) {
        val card = uiState.value.items
            .filterIsInstance<ChatItem.TaskCard>()
            .firstOrNull { it.id == itemId } ?: return
        if (card.undone) return

        viewModelScope.launch {
            taskCreator.undo(card.taskId)
            chatRepository.markUndone(itemId)
            chatRepository.insert(
                ChatEntry(type = ChatEntryType.ASSISTANT, content = "已撤销「${card.draft.title}」。")
            )
        }
    }

    /** 清空上下文：插入分隔线，之后的对话不再携带此前历史 */
    fun clearContext() {
        if (processing.value) return
        // 当前上下文本来就是空的，无需重复插入分隔线
        val lastItem = uiState.value.items.lastOrNull()
        if (lastItem == null || lastItem is ChatItem.Divider || lastItem == GREETING) return

        viewModelScope.launch {
            chatRepository.insertDivider()
            chatRepository.insert(
                ChatEntry(
                    type = ChatEntryType.ASSISTANT,
                    content = "已开启新对话，上面的聊天记录不会再作为上下文。",
                )
            )
        }
    }

    // ---------- private ----------

    /** 每个创建结果一张卡片 + 一条收尾确认文本，全部持久化 */
    private suspend fun persistCreated(reply: AgentReply.TasksCreated) {
        reply.created.forEach { created ->
            chatRepository.insert(
                ChatEntry(
                    type = ChatEntryType.TASK_CARD,
                    taskId = created.taskId,
                    draft = created.draft,
                )
            )
        }
        val summary = reply.message?.takeIf { it.isNotBlank() } ?: buildSummary(reply.created)
        chatRepository.insert(ChatEntry(type = ChatEntryType.ASSISTANT, content = summary))
    }

    private fun buildSummary(created: List<CreatedTask>): String = buildString {
        if (created.size == 1) {
            val draft = created.first().draft
            append("已添加「${draft.title}」到${draft.category.label}清单")
            draft.remindAtMillis?.let { append("，将在 ${formatFriendlyTime(it)} 提醒你") }
            append("。")
        } else {
            append("已添加 ${created.size} 个任务：")
            append(created.joinToString("、") { "「${it.draft.title}」" })
            append("。")
        }
        if (created.any { it.draft.fromOfflineFallback }) append("（离线解析，建议核对时间）")
    }

    private fun ChatEntry.toChatItem(): ChatItem? = when (type) {
        ChatEntryType.USER -> ChatItem.User(id, content)
        ChatEntryType.ASSISTANT -> ChatItem.Assistant(id, content, isError)
        ChatEntryType.TASK_CARD -> draft?.let { ChatItem.TaskCard(id, it, taskId ?: 0L, undone) }
        ChatEntryType.DIVIDER -> ChatItem.Divider(id)
    }

    private companion object {
        const val MAX_INPUT_LENGTH = 500

        val GREETING = ChatItem.Assistant(
            -1L,
            "你好！输入你的任务，比如「明天下午三点开产品评审会」，我会自动分类、加入清单并设置提醒。一句话说多个任务也可以。",
        )
    }
}
