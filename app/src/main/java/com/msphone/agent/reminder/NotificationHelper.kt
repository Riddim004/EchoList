package com.msphone.agent.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.msphone.agent.MainActivity
import com.msphone.agent.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 任务提醒通知：高优先级渠道，带"完成 / 稍后提醒"动作按钮 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showTaskReminder(taskId: Long, title: String) {
        ensureChannel()

        val contentIntent = PendingIntent.getActivity(
            context, taskId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("任务提醒")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.action_done), actionIntent(taskId, title, NotificationActionReceiver.ACTION_DONE))
            .addAction(0, context.getString(R.string.action_snooze), actionIntent(taskId, title, NotificationActionReceiver.ACTION_SNOOZE))
            .build()

        runCatching {
            // 无通知权限时 notify 会抛 SecurityException，静默忽略
            NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
        }
    }

    fun cancel(taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }

    private fun actionIntent(taskId: Long, title: String, action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
            putExtra(ReminderScheduler.EXTRA_TITLE, title)
        }
        // action 参与 requestCode，避免完成/稍后两个 PendingIntent 相互覆盖
        val requestCode = (taskId * 10 + if (action == NotificationActionReceiver.ACTION_DONE) 1 else 2).toInt()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "task_reminder"
    }
}
