package com.msphone.agent.domain.model

/** 提醒方式：决定任务提醒通知走哪个通知渠道 */
enum class ReminderMode(val label: String, val description: String) {
    RING("响铃", "播放提示音并震动"),
    VIBRATE("震动", "只震动，不播放提示音"),
    SILENT("静音", "只弹通知，不响不震"),
}

/** 主题模式 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}
