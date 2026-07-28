package com.msphone.agent.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 精确闹钟调度：Doze 模式可触发；无精确闹钟权限时降级为 10 分钟窗口。
 * 数据库是唯一事实来源，闹钟仅作触发器。
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(taskId: Long, title: String, remindAtMillis: Long) {
        if (remindAtMillis <= System.currentTimeMillis()) return
        val pendingIntent = buildPendingIntent(taskId, title)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAtMillis, pendingIntent)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, remindAtMillis, WINDOW_MILLIS, pendingIntent)
        }
    }

    fun cancel(taskId: Long) {
        alarmManager.cancel(buildPendingIntent(taskId, ""))
    }

    /** Android 12+ 需要精确闹钟特殊权限 */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun buildPendingIntent(taskId: Long, title: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(), // taskId 作 requestCode，保证每个任务的闹钟互不覆盖
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        private const val WINDOW_MILLIS = 10 * 60_000L
    }
}
