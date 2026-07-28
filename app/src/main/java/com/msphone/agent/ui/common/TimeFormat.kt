package com.msphone.agent.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * UI 展示用的友好时间格式（聊天页用）：
 * 今天/明天/后天直接用相对称呼，其余日期附带星期几；跨年才显示年份。
 * （模型侧 payload 仍用 tools 里的 formatMillis，两者职责分离）
 */
fun formatFriendlyTime(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val target = Instant.ofEpochMilli(millis).atZone(zone)
    val date = target.toLocalDate()
    val today = LocalDate.now(zone)
    val time = target.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (date) {
        today -> "今天 $time"
        today.plusDays(1) -> "明天 $time"
        today.plusDays(2) -> "后天 $time"
        else -> formatDateWeekTime(millis)
    }
}

/**
 * 完整日期格式（任务卡片时间行用）：始终显示具体日期 + 星期几，
 * 不用今天/明天相对称呼（相对信息由卡片角标承担，避免重复）；跨年才显示年份。
 */
fun formatDateWeekTime(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val target = Instant.ofEpochMilli(millis).atZone(zone)
    val date = target.toLocalDate()
    val today = LocalDate.now(zone)
    val time = target.format(DateTimeFormatter.ofPattern("HH:mm"))
    val week = "周" + "一二三四五六日"[date.dayOfWeek.value - 1]
    val datePart =
        if (date.year == today.year) target.format(DateTimeFormatter.ofPattern("M月d日"))
        else target.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    return "$datePart $week $time"
}

/** 相对日期标签：今日/明日到期返回标签文本，其余返回 null（卡片角标用） */
fun relativeDayTag(millis: Long): String? {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "今日"
        today.plusDays(1) -> "明日"
        else -> null
    }
}
