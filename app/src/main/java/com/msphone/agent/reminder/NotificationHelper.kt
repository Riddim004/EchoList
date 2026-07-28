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
import com.msphone.agent.data.settings.SettingsRepository
import com.msphone.agent.domain.model.ReminderMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务提醒通知：带"完成 / 稍后提醒"动作按钮。
 * Android O+ 渠道的声音/震动创建后不可改，因此预建响铃/震动/静音三条渠道，
 * 发通知时按用户设置的提醒方式选择对应渠道。
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        // 旧版单渠道已拆分为三渠道，删除避免在系统设置里重复展示
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)

        val ring = NotificationChannel(
            CHANNEL_RING,
            context.getString(R.string.channel_ring_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            enableVibration(true)
        }
        val vibrate = NotificationChannel(
            CHANNEL_VIBRATE,
            context.getString(R.string.channel_vibrate_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            setSound(null, null)
            enableVibration(true)
        }
        val silent = NotificationChannel(
            CHANNEL_SILENT,
            context.getString(R.string.channel_silent_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannels(listOf(ring, vibrate, silent))
    }

    fun showTaskReminder(taskId: Long, title: String) {
        ensureChannels()
        // 闹钟广播回调里同步读一次设置（DataStore 小文件，毫秒级）
        val channelId = when (runBlocking { settings.reminderMode.first() }) {
            ReminderMode.RING -> CHANNEL_RING
            ReminderMode.VIBRATE -> CHANNEL_VIBRATE
            ReminderMode.SILENT -> CHANNEL_SILENT
        }

        val contentIntent = PendingIntent.getActivity(
            context, taskId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
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
        private const val LEGACY_CHANNEL_ID = "task_reminder"
        const val CHANNEL_RING = "task_reminder_ring"
        const val CHANNEL_VIBRATE = "task_reminder_vibrate"
        const val CHANNEL_SILENT = "task_reminder_silent"
    }
}
