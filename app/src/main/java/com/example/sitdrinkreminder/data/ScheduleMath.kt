package com.example.sitdrinkreminder.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 纯计算工具：不碰 Android API，方便单测，也方便二次开发时替换调度方案。
 */
object ScheduleMath {

    const val MIN_INTERVAL_MINUTES = 5
    const val MAX_INTERVAL_MINUTES = 24 * 60
    const val DEFAULT_INTERVAL_MINUTES = 45

    /** 间隔选择弹窗里的预设值（分钟） */
    val INTERVAL_PRESETS = intArrayOf(15, 30, 45, 60, 90, 120, 180)

    /** 错过了多久之内还值得补发一次（超过就重新计时，避免开机后被旧提醒刷屏） */
    const val CATCH_UP_WINDOW_MILLIS = 5 * 60_000L

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_DAY = 24 * 60

    fun coerceInterval(minutes: Int): Int =
        minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

    fun intervalMillis(intervalMinutes: Int): Long =
        coerceInterval(intervalMinutes) * MILLIS_PER_MINUTE

    /** 从现在开始计时，一个间隔之后触发 */
    fun firstTriggerFromNow(
        intervalMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long = nowMillis + intervalMillis(intervalMinutes)

    /**
     * 修正一个「已有的」触发时间，用于开机/回前台时的自愈重排：
     * - 还在未来：原样保留（不会因为打开 App 就把提醒往后推）
     * - 刚刚错过（[CATCH_UP_WINDOW_MILLIS] 内）：马上补发一次，用户不至于整天漏掉
     * - 错过太久（例如关机一夜）：从现在重新计时，避免开机瞬间被旧提醒轰炸
     */
    fun ensureFuture(
        candidateMillis: Long,
        intervalMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long = when {
        candidateMillis > nowMillis -> candidateMillis
        candidateMillis > nowMillis - CATCH_UP_WINDOW_MILLIS -> nowMillis + 1_000L
        else -> nowMillis + intervalMillis(intervalMinutes)
    }

    /**
     * 计算 [timesOfDay] 中严格晚于 [fromMillis] 的下一个触发时间。
     * 时区变化/夏令时由 java.time 处理；没有可用时间点时返回 null。
     */
    fun nextFixedOccurrence(
        timesOfDay: List<Int>,
        fromMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val minutesOfDay = timesOfDay
            .filter { it in 0 until MINUTES_PER_DAY }
            .distinct()
            .sorted()
        if (minutesOfDay.isEmpty()) return null

        val from = Instant.ofEpochMilli(fromMillis).atZone(zone)
        val today = from.toLocalDate()
        // 最多看 8 天，足以覆盖任何时间点组合（包括只设了今天已过的时间点）
        for (dayOffset in 0..7) {
            val date = today.plusDays(dayOffset.toLong())
            for (minutes in minutesOfDay) {
                val candidate = date
                    .atTime(minutes / 60, minutes % 60)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                if (candidate > fromMillis + 1_000L) return candidate
            }
        }
        return null
    }

    /** 570 -> "09:30" */
    fun formatMinutesOfDay(minutesOfDay: Int): String {
        val safe = minutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }

    /** "09:30" -> 570，格式不合法返回 null */
    fun parseMinutesOfDay(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
}
