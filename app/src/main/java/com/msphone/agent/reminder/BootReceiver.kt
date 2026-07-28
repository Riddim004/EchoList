package com.msphone.agent.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.msphone.agent.domain.repository.TaskRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设备重启后从数据库恢复所有未触发的提醒闹钟（闹钟对账） */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: TaskRepository

    @Inject
    lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderReconciler.reconcile(repository, scheduler)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** 闹钟对账：遍历未完成且未过期的任务，重新注册闹钟（开机恢复 / 应用启动共用） */
object ReminderReconciler {

    suspend fun reconcile(repository: TaskRepository, scheduler: ReminderScheduler) {
        val now = System.currentTimeMillis()
        repository.getPendingWithReminder().forEach { task ->
            val remindAt = task.remindAtMillis ?: return@forEach
            if (remindAt > now) {
                scheduler.schedule(task.id, task.title, remindAt)
            }
        }
    }
}
