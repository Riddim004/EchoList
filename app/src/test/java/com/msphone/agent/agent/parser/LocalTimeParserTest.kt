package com.msphone.agent.agent.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 本地规则时间解析单元测试。
 * 基准时间固定为 2026-07-27（周一）14:30。
 */
class LocalTimeParserTest {

    private val base: LocalDateTime = LocalDateTime.of(2026, 7, 27, 14, 30)

    private fun parseToLocal(text: String): LocalDateTime? =
        LocalTimeParser.parse(text, base)?.let {
            Instant.ofEpochMilli(it.timeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        }

    @Test
    fun `明天下午三点`() {
        assertEquals(LocalDateTime.of(2026, 7, 28, 15, 0), parseToLocal("明天下午三点开产品评审会"))
    }

    @Test
    fun `后天早上8点半`() {
        assertEquals(LocalDateTime.of(2026, 7, 29, 8, 30), parseToLocal("后天早上8点半去医院"))
    }

    @Test
    fun `两小时后`() {
        assertEquals(LocalDateTime.of(2026, 7, 27, 16, 30), parseToLocal("两小时后取快递"))
    }

    @Test
    fun `30分钟后`() {
        assertEquals(LocalDateTime.of(2026, 7, 27, 15, 0), parseToLocal("30分钟后开会"))
    }

    @Test
    fun `半个小时后`() {
        assertEquals(LocalDateTime.of(2026, 7, 27, 15, 0), parseToLocal("半个小时后提醒我喝水"))
    }

    @Test
    fun `周五早上默认8点`() {
        // 基准是周一，周五 = 7月31日
        assertEquals(LocalDateTime.of(2026, 7, 31, 8, 0), parseToLocal("周五早上交周报"))
    }

    @Test
    fun `下周三默认9点`() {
        // 基准是周一 7-27，下周三 = 8月5日
        assertEquals(LocalDateTime.of(2026, 8, 5, 9, 0), parseToLocal("下周三团建"))
    }

    @Test
    fun `明晚默认20点`() {
        assertEquals(LocalDateTime.of(2026, 7, 28, 20, 0), parseToLocal("明晚看电影"))
    }

    @Test
    fun `明天中午默认12点`() {
        assertEquals(LocalDateTime.of(2026, 7, 28, 12, 0), parseToLocal("明天中午和客户吃饭"))
    }

    @Test
    fun `X月X日`() {
        assertEquals(LocalDateTime.of(2026, 8, 15, 9, 0), parseToLocal("8月15日交房租"))
    }

    @Test
    fun `已过日期顺延到明年`() {
        assertEquals(LocalDateTime.of(2027, 3, 1, 9, 0), parseToLocal("3月1日体检"))
    }

    @Test
    fun `仅时刻且已过时顺延次日`() {
        // 基准 14:30，"上午十点"已过 → 次日 10:00
        assertEquals(LocalDateTime.of(2026, 7, 28, 10, 0), parseToLocal("上午十点开会"))
    }

    @Test
    fun `无时间表达返回null`() {
        assertNull(parseToLocal("记得买牛奶"))
    }

    @Test
    fun `表达式字段包含原文时间`() {
        val parsed = LocalTimeParser.parse("明天下午三点开会", base)
        assertNotNull(parsed)
        assertEquals("明天下午三点", parsed!!.expression)
    }

    @Test
    fun `中文数字转换`() {
        assertEquals(3, LocalTimeParser.chineseToInt("三"))
        assertEquals(10, LocalTimeParser.chineseToInt("十"))
        assertEquals(15, LocalTimeParser.chineseToInt("十五"))
        assertEquals(20, LocalTimeParser.chineseToInt("二十"))
        assertEquals(24, LocalTimeParser.chineseToInt("二十四"))
        assertEquals(8, LocalTimeParser.chineseToInt("8"))
    }
}
