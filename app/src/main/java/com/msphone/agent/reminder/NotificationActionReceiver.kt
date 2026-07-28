package com.msphone.agent.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.msphone.agent.domain.model.TaskStatus
import com.msphone.agent.domain.repository.TaskRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 处理通知上的"完成 / 稍后提醒（+10 分钟）"动作 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: TaskRepository

    @Inject
    lateinit var scheduler: ReminderScheduler

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: ""
        notificationHelper.cancel(taskId)

        when (intent.action) {
            ACTION_DONE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        repository.updateStatus(taskId, TaskStatus.DONE)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_SNOOZE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val snoozeAt = System.currentTimeMillis() + SNOOZE_MILLIS
                        repository.updateRemindTime(taskId, snoozeAt)
                        scheduler.schedule(taskId, title, snoozeAt)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.msphone.agent.action.TASK_DONE"
        const val ACTION_SNOOZE = "com.msphone.agent.action.TASK_SNOOZE"
        private const val SNOOZE_MILLIS = 10 * 60_000L
    }
}
