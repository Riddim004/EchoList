package com.msphone.agent.ui.tasks

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msphone.agent.domain.model.Task
import com.msphone.agent.domain.model.TaskCategory
import com.msphone.agent.domain.model.TaskStatus
import com.msphone.agent.ui.common.formatDateWeekTime
import com.msphone.agent.ui.common.relativeDayTag
import com.msphone.agent.ui.theme.DayTagColor
import com.msphone.agent.ui.theme.LifeColor
import com.msphone.agent.ui.theme.WorkColor
import kotlinx.coroutines.launch

/** 任务页：全部 / 工作 / 生活 / 已完成 */
@Composable
fun TaskListScreen(viewModel: TaskListViewModel = hiltViewModel()) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskListViewModel.Filter.entries.forEach { item ->
                    // 待办类筛选项带数量括号，已完成不显示
                    val label = counts[item]?.let { "${item.label}($it)" } ?: item.label
                    FilterChip(
                        selected = filter == item,
                        onClick = { viewModel.setFilter(item) },
                        label = { Text(label) },
                    )
                }
            }

            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无任务，去「助手」页添加吧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onToggleDone = {
                                if (task.status == TaskStatus.DONE) viewModel.reopen(task)
                                else viewModel.complete(task)
                            },
                            onDelete = {
                                viewModel.delete(task)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "已删除「${task.title}」",
                                        actionLabel = "撤销",
                                    )
                                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: Task, onToggleDone: () -> Unit, onDelete: () -> Unit) {
    val done = task.status == TaskStatus.DONE
    val categoryColor = if (task.category == TaskCategory.WORK) WorkColor else LifeColor

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = done, onCheckedChange = { onToggleDone() })

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    // 今日/明日角标：橙色系，与工作蓝/生活绿区分；已完成不显示
                    if (!done) {
                        task.remindAtMillis?.let(::relativeDayTag)?.let { tag ->
                            Surface(
                                color = DayTagColor.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DayTagColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    Surface(
                        color = categoryColor.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            task.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                task.remindAtMillis?.let { remindAt ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.height(14.dp),
                        )
                        Text(
                            " ${formatDateWeekTime(remindAt)} · ${countdownText(remindAt, done)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun countdownText(remindAt: Long, done: Boolean): String {
    if (done) return "已完成"
    val diff = remindAt - System.currentTimeMillis()
    if (diff <= 0) return "已过期"
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "即将提醒"
        minutes < 60 -> "${minutes}分钟后"
        minutes < 24 * 60 -> "${minutes / 60}小时${minutes % 60}分后"
        else -> "${minutes / (24 * 60)}天后"
    }
}
