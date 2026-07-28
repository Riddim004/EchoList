package com.msphone.agent.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.msphone.agent.domain.model.ReminderMode
import com.msphone.agent.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 应用设置仓库：提醒方式 / 主题模式，DataStore 持久化 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val reminderMode: Flow<ReminderMode> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_REMINDER_MODE]
            ?.let { raw -> runCatching { ReminderMode.valueOf(raw) }.getOrNull() }
            ?: ReminderMode.RING
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]
            ?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setReminderMode(mode: ReminderMode) {
        context.settingsDataStore.edit { it[KEY_REMINDER_MODE] = mode.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    private companion object {
        val KEY_REMINDER_MODE = stringPreferencesKey("reminder_mode")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
