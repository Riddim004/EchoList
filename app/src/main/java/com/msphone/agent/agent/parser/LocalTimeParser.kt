package com.msphone.agent.agent.parser

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 本地规则时间解析引擎（离线降级通道，F8）。
 * 覆盖高频中文时间表达：明天/后天/周X/X月X日/X点半/X小时后 等。
 */
object LocalTimeParser {

    data class Parsed(val timeMillis: Long, val expression: String)

    private data class DayPart(val date: LocalDate, val defaultHour: Int?, val expression: String)
    private data class ClockPart(val hour: Int, val minute: Int, val expression: String)

    private val relativeOffsetRegex =
        Regex("(半|[一二两三四五六七八九十]+|\\d+)\\s*个?\\s*(小时|分钟)\\s*(之?[后後]|以后)")
    private val monthDayRegex = Regex("(\\d{1,2})月(\\d{1,2})[日号]")
    private val weekRegex = Regex("(下+)?(?:周|星期|礼拜)([一二三四五六日天])")
    private val clockRegex =
        Regex("(凌晨|早上|上午|中午|下午|晚上)?\\s*([0-9]{1,2}|[一二两三四五六七八九十]+)\\s*[点时]\\s*(半|一刻|三刻|[0-9]{1,2}分?)?")
    private val periodOnlyRegex = Regex("凌晨|早上|上午|中午|下午|晚上")

    /** 相对日期关键词（顺序敏感：长词优先） */
    private val dayKeywords = listOf(
        "大后天" to Triple(3L, null, "大后天"),
        "后天" to Triple(2L, null, "后天"),
        "明晚" to Triple(1L, 20, "明晚"),
        "明早" to Triple(1L, 8, "明早"),
        "明天" to Triple(1L, null, "明天"),
        "今晚" to Triple(0L, 20, "今晚"),
        "今天" to Triple(0L, null, "今天"),
    )

    private val weekDayMap = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7,
    )

    fun parse(text: String, base: LocalDateTime = LocalDateTime.now()): Parsed? {
        parseRelativeOffset(text, base)?.let { return it }

        val dayPart = findDay(text, base)
        val clockPart = findClock(text)
        if (dayPart == null && clockPart == null) return null

        val date = dayPart?.date ?: base.toLocalDate()
        val hour = clockPart?.hour ?: dayPart?.defaultHour ?: DEFAULT_HOUR
        val minute = clockPart?.minute ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null

        var dateTime = LocalDateTime.of(date, LocalTime.of(hour, minute))
        // 只有时刻没有日期，且时刻已过 → 顺延到次日
        if (dayPart == null && !dateTime.isAfter(base)) {
            dateTime = dateTime.plusDays(1)
        }

        val expression = listOfNotNull(dayPart?.expression, clockPart?.expression)
            .joinToString("")
            .ifBlank { return null }
        return Parsed(dateTime.toMillis(), expression)
    }

    /** "两小时后 / 30分钟后 / 半个小时后" */
    private fun parseRelativeOffset(text: String, base: LocalDateTime): Parsed? {
        val match = relativeOffsetRegex.find(text) ?: return null
        val (numText, unit) = match.destructured
        val minutes = when {
            numText == "半" -> if (unit == "小时") 30L else return null
            else -> {
                val value = chineseToInt(numText)?.toLong() ?: return null
                if (unit == "小时") value * 60 else value
            }
        }
        if (minutes <= 0) return null
        return Parsed(base.plusMinutes(minutes).toMillis(), match.value)
    }

    private fun findDay(text: String, base: LocalDateTime): DayPart? {
        // 1. 相对日期关键词
        dayKeywords.firstOrNull { (keyword, _) -> keyword in text }?.let { (_, info) ->
            val (offsetDays, defaultHour, expression) = info
            return DayPart(base.toLocalDate().plusDays(offsetDays), defaultHour, expression)
        }
        // 2. X月X日
        monthDayRegex.find(text)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            if (month !in 1..12 || day !in 1..31) return@let
            var date = runCatching { LocalDate.of(base.year, month, day) }.getOrNull() ?: return@let
            // 日期已过 → 视为明年
            if (date.isBefore(base.toLocalDate())) date = date.plusYears(1)
            return DayPart(date, null, match.value)
        }
        // 3. 周X / 下周X
        weekRegex.find(text)?.let { match ->
            val nextWeeks = match.groupValues[1].length // "下" 的个数
            val target = weekDayMap[match.groupValues[2].first()] ?: return@let
            val current = base.dayOfWeek.value
            var delta = ((target - current) % 7 + 7) % 7
            // "周三"若已是过去（今天之前）则取下周；delta==0 表示今天
            delta += nextWeeks * 7
            return DayPart(base.toLocalDate().plusDays(delta.toLong()), null, match.value)
        }
        return null
    }

    private fun findClock(text: String): ClockPart? {
        clockRegex.find(text)?.let { match ->
            val period = match.groupValues[1]
            var hour = chineseToInt(match.groupValues[2]) ?: return@let
            val minutePart = match.groupValues[3]
            val minute = when {
                minutePart == "半" -> 30
                minutePart == "一刻" -> 15
                minutePart == "三刻" -> 45
                minutePart.isNotBlank() -> minutePart.removeSuffix("分").toIntOrNull() ?: 0
                else -> 0
            }
            hour = adjustByPeriod(hour, period)
            if (hour !in 0..23) return@let
            return ClockPart(hour, minute, match.value)
        }
        // 只有时段词没有具体时刻："明天中午吃饭"
        periodOnlyRegex.find(text)?.let { match ->
            val hour = when (match.value) {
                "凌晨" -> 6
                "早上" -> 8
                "上午" -> 9
                "中午" -> 12
                "下午" -> 15
                else -> 20 // 晚上
            }
            return ClockPart(hour, 0, match.value)
        }
        return null
    }

    private fun adjustByPeriod(hour: Int, period: String): Int = when (period) {
        "下午", "晚上" -> if (hour < 12) hour + 12 else hour
        "中午" -> if (hour <= 2) hour + 12 else hour // "中午一点" = 13:00
        else -> hour
    }

    /** 中文数字 → 整数（支持 0-99：三、十、十五、二十、二十四…） */
    internal fun chineseToInt(text: String): Int? {
        text.toIntOrNull()?.let { return it }
        val digits = mapOf(
            '零' to 0, '一' to 1, '两' to 2, '二' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        return when {
            text == "十" -> 10
            text.startsWith("十") && text.length == 2 -> digits[text[1]]?.plus(10)
            text.endsWith("十") && text.length == 2 -> digits[text[0]]?.times(10)
            text.length == 3 && text[1] == '十' ->
                digits[text[0]]?.let { tens -> digits[text[2]]?.let { tens * 10 + it } }
            text.length == 1 -> digits[text[0]]
            else -> null
        }
    }

    private fun LocalDateTime.toMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private const val DEFAULT_HOUR = 9
}
