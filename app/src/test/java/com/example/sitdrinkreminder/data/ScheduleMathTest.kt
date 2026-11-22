package com.example.sitdrinkreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 调度计算的纯 JVM 单元测试（不依赖 Android 框架）：
 *
 *   ./gradlew test
 *
 * 这些用例覆盖了“间隔计时 / 开机补排 / 固定时间点跨天 / 边界值”四种核心逻辑。
 */
class ScheduleMathTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun intervalIsClampedToLegalRange() {
        assertEquals(5, ScheduleMath.coerceInterval(0))
        assertEquals(5, ScheduleMath.coerceInterval(-30))
        assertEquals(1440, ScheduleMath.coerceInterval(99999))
        assertEquals(45, ScheduleMath.coerceInterval(45))
    }

    @Test
    fun firstTriggerIsNowPlusInterval() {
        val now = millis(2026, 3, 1, 9, 0)
        assertEquals(now + 45 * 60_000L, ScheduleMath.firstTriggerFromNow(45, now))
    }

    @Test
    fun ensureFutureKeepsFutureTime() {
        val now = millis(2026, 3, 1, 9, 0)
        val future = now + 10 * 60_000L
        assertEquals(future, ScheduleMath.ensureFuture(future, 30, now))
    }

    @Test
    fun ensureFutureReanchorsFromNowWhenMissed() {
        val now = millis(2026, 3, 1, 9, 0)
        val missed = now - 6 * 60 * 60_000L
        // 关机几小时后开机：不补发旧提醒，而是从现在起重新计时
        assertEquals(now + 30 * 60_000L, ScheduleMath.ensureFuture(missed, 30, now))
    }

    @Test
    fun ensureFutureCatchesUpWhenJustMissed() {
        val now = millis(2026, 3, 1, 9, 2)
        val missed = millis(2026, 3, 1, 9, 0) // 2 分钟前刚错过
        // 补发一次（排在 1 秒后），而不是直接跳到下一个间隔
        assertEquals(now + 1_000L, ScheduleMath.ensureFuture(missed, 30, now))
    }

    @Test
    fun nextFixedOccurrenceFindsLaterSlotToday() {
        val from = millis(2026, 3, 1, 8, 0)
        val times = listOf(14 * 60, 9 * 60) // 故意乱序，内部会排序
        assertEquals(
            millis(2026, 3, 1, 9, 0),
            ScheduleMath.nextFixedOccurrence(times, from, zone)
        )
    }

    @Test
    fun nextFixedOccurrenceRollsOverToTomorrow() {
        val from = millis(2026, 3, 1, 15, 0)
        val times = listOf(9 * 60, 14 * 60)
        assertEquals(
            millis(2026, 3, 2, 9, 0),
            ScheduleMath.nextFixedOccurrence(times, from, zone)
        )
    }

    @Test
    fun nextFixedOccurrenceSkipsTheCurrentMinute() {
        // 正好 09:00 时不应再返回 09:00，否则会立刻重复触发
        val from = millis(2026, 3, 1, 9, 0)
        assertEquals(
            millis(2026, 3, 2, 9, 0),
            ScheduleMath.nextFixedOccurrence(listOf(9 * 60), from, zone)
        )
    }

    @Test
    fun nextFixedOccurrenceHandlesMonthBoundary() {
        val from = millis(2026, 1, 31, 20, 0)
        assertEquals(
            millis(2026, 2, 1, 8, 0),
            ScheduleMath.nextFixedOccurrence(listOf(8 * 60), from, zone)
        )
    }

    @Test
    fun nextFixedOccurrenceReturnsNullWithoutTimes() {
        assertNull(ScheduleMath.nextFixedOccurrence(emptyList(), millis(2026, 3, 1, 9, 0), zone))
        assertNull(ScheduleMath.nextFixedOccurrence(listOf(-1, 24 * 60), millis(2026, 3, 1, 9, 0), zone))
    }

    @Test
    fun formatAndParseMinutesOfDay() {
        assertEquals("09:30", ScheduleMath.formatMinutesOfDay(9 * 60 + 30))
        assertEquals("00:00", ScheduleMath.formatMinutesOfDay(0))
        assertEquals("23:59", ScheduleMath.formatMinutesOfDay(23 * 60 + 59))
        assertEquals(570, ScheduleMath.parseMinutesOfDay("09:30") ?: -1)
        assertEquals(0, ScheduleMath.parseMinutesOfDay("00:00") ?: -1)
    }

    @Test
    fun parseRejectsInvalidInput() {
        assertNull(ScheduleMath.parseMinutesOfDay("24:00"))
        assertNull(ScheduleMath.parseMinutesOfDay("12:60"))
        assertNull(ScheduleMath.parseMinutesOfDay("abc"))
        assertNull(ScheduleMath.parseMinutesOfDay("12"))
    }
}
