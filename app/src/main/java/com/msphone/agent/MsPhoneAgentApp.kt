package com.msphone.agent

import android.app.Application
import com.msphone.agent.domain.repository.ChatRepository
import com.msphone.agent.domain.repository.TaskRepository
import com.msphone.agent.reminder.NotificationHelper
import com.msphone.agent.reminder.ReminderReconciler
import com.msphone.agent.reminder.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MsPhoneAgentApp : Application() {

    @Inject
    lateinit var repository: TaskRepository

    @Inject
    lateinit var chatRepository: ChatRepository

    @Inject
    lateinit var scheduler: ReminderScheduler

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannels()
        // 启动时闹钟对账：重新注册缺失的提醒闹钟；同时清理超过保留期的旧聊天记录
        appScope.launch {
            ReminderReconciler.reconcile(repository, scheduler)
            chatRepository.prune(CHAT_RETENTION_MILLIS)
        }
    }

    private companion object {
        /** 聊天记录保留期：7 天（当前会话永不误删） */
        const val CHAT_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
