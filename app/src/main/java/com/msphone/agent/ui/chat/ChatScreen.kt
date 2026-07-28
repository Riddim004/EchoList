package com.msphone.agent.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msphone.agent.domain.model.TaskCategory
import com.msphone.agent.ui.common.formatFriendlyTime
import com.msphone.agent.ui.theme.LifeColor
import com.msphone.agent.ui.theme.WorkColor
import kotlinx.coroutines.delay

/** 助手页：顶栏 + 聊天流 + 任务结果卡片 + 文字输入栏 */
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 新消息自动滚动到底部
    LaunchedEffect(uiState.items.size, uiState.processing) {
        if (uiState.items.isNotEmpty()) listState.animateScrollToItem(uiState.items.size)
    }

    Column(Modifier.fillMaxSize()) {
        HeaderBar(onOpenSettings = onOpenSettings, onClearContext = viewModel::clearContext)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(uiState.items, key = { it.id }) { item ->
                when (item) {
                    is ChatViewModel.ChatItem.User -> UserBubble(item.text)
                    is ChatViewModel.ChatItem.Assistant -> AssistantBubble(item.text, item.isError)
                    is ChatViewModel.ChatItem.TaskCard -> TaskCardView(
                        card = item,
                        onUndo = { viewModel.undoTask(item.id) },
                    )
                    is ChatViewModel.ChatItem.Divider -> ContextDivider()
                }
            }
            if (uiState.processing) {
                item(key = "processing") { ProcessingBubble() }
            }
        }

        InputBar(
            value = input,
            onValueChange = { input = it.take(500) },
            sendEnabled = !uiState.processing && input.isNotBlank(),
            onSend = {
                viewModel.send(input)
                input = ""
            },
        )
    }
}

@Composable
private fun HeaderBar(onOpenSettings: () -> Unit, onClearContext: () -> Unit) {
    Surface(shadowElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "智能助手",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClearContext) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = "清空上下文",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 上下文分隔线：其后为新会话 */
@Composable
private fun ContextDivider() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            "  新对话  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            // SelectionContainer 支持长按选择复制
            SelectionContainer {
                Text(
                    text,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String, isError: Boolean) {
    // 回复文本到达时已是完整内容，立即全量展示，不做伪流式动画人为拖慢
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            color = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            SelectionContainer {
                Text(
                    text,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ProcessingBubble() {
    // 动态省略号：正在思考. / .. / ...
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dots = dots % 3 + 1
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.height(16.dp).widthIn(max = 16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.widthIn(min = 8.dp))
        Text(
            "  正在思考" + ".".repeat(dots),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 已创建任务的结果卡片：任务已自动落库，按钮用于撤销 */
@Composable
private fun TaskCardView(
    card: ChatViewModel.ChatItem.TaskCard,
    onUndo: () -> Unit,
) {
    val draft = card.draft
    val categoryColor = if (draft.category == TaskCategory.WORK) WorkColor else LifeColor

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    draft.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label = { Text(draft.category.label, color = categoryColor) },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(18.dp),
                )
                Spacer(Modifier.widthIn(min = 6.dp))
                Text(
                    draft.remindAtMillis?.let { " ${formatFriendlyTime(it)}" } ?: " 不设提醒",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                draft.timeExpression?.let {
                    Text(
                        "（$it）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            if (draft.adjustedToNextDay) {
                Text(
                    "⚠ 该时间已过，已自动顺延到次日，如不对请撤销后重新输入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (draft.fromOfflineFallback) {
                Text(
                    "离线解析结果（网络恢复后建议核对）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            if (card.undone) {
                Text(
                    "已撤销",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = LifeColor,
                        modifier = Modifier.height(18.dp),
                    )
                    Text(" 已添加到清单", color = LifeColor, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onUndo) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = null,
                            modifier = Modifier.height(16.dp),
                        )
                        Text(" 撤销")
                    }
                }
            }
        }
    }
}

/** 底部输入栏：文本框 + 发送 */
@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("输入任务，如：明天下午三点开会") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(Modifier.widthIn(min = 8.dp))
            FilledIconButton(onClick = onSend, enabled = sendEnabled) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}
