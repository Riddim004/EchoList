package com.msphone.agent.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 闹钟到点触发 → 发送提醒通知 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "你有一个任务待处理"
        notificationHelper.showTaskReminder(taskId, title)
    }
}
