package com.msphone.agent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.msphone.agent.data.settings.SettingsRepository
import com.msphone.agent.domain.model.ReminderMode
import com.msphone.agent.domain.model.ThemeMode
import com.msphone.agent.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    val reminderMode: StateFlow<ReminderMode> = settings.reminderMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReminderMode.RING)

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setReminderMode(mode: ReminderMode) {
        viewModelScope.launch { settings.setReminderMode(mode) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /** 清空助手页全部聊天记录（任务数据不受影响） */
    fun clearChatHistory() {
        viewModelScope.launch { chatRepository.clearAll() }
    }
}
