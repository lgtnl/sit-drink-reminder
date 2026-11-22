package com.example.sitdrinkreminder.util

import android.content.Context
import com.example.sitdrinkreminder.R
import com.example.sitdrinkreminder.data.ReminderConfig
import com.example.sitdrinkreminder.data.ScheduleMath
import com.example.sitdrinkreminder.data.ScheduleMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 界面文案拼装。集中在此处，避免 Activity 里散落一堆字符串拼接。
 */
object UiText {

    private val CLOCK_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    private val DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日", Locale.getDefault())

    /** "每 45 分钟提醒一次" / "每天 09:30、11:00 提醒" */
    fun describeSchedule(context: Context, config: ReminderConfig): String {
        if (config.mode == ScheduleMode.FIXED_TIME) {
            if (config.timesOfDay.isEmpty()) {
                return context.getString(R.string.schedule_none_summary)
            }
            val times = config.timesOfDay
                .sorted()
                .joinToString(separator = "、") { ScheduleMath.formatMinutesOfDay(it) }
            return context.getString(R.string.schedule_times_summary, times)
        }
        return context.getString(R.string.schedule_interval_summary, config.intervalMinutes)
    }

    /** "今天 14:30" / "明天 09:00" / "11月25日 09:00" */
    fun describeMoment(
        context: Context,
        atMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val zone = ZoneId.systemDefault()
        val target = Instant.ofEpochMilli(atMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)

        val day = when {
            target.toLocalDate() == now.toLocalDate() -> context.getString(R.string.day_today)
            target.toLocalDate() == now.toLocalDate().plusDays(1) -> context.getString(R.string.day_tomorrow)
            else -> DATE_FORMAT.format(target)
        }
        return context.getString(R.string.moment_format, day, CLOCK_FORMAT.format(target))
    }

    /** "下次提醒：今天 14:30（还有 32 分钟）" */
    fun describeNextTrigger(
        context: Context,
        nextTriggerAt: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (nextTriggerAt <= 0L) return context.getString(R.string.next_time_not_configured)

        val moment = describeMoment(context, nextTriggerAt, nowMillis)
        val remaining = describeRemaining(context, nextTriggerAt - nowMillis)
        return if (remaining == null) {
            context.getString(R.string.next_time_format, moment)
        } else {
            context.getString(R.string.next_time_format_with_remaining, moment, remaining)
        }
    }

    private fun describeRemaining(context: Context, remainingMillis: Long): String? {
        if (remainingMillis <= 0L) return context.getString(R.string.remaining_now)

        val totalMinutes = remainingMillis / 60_000L
        if (totalMinutes < 60) {
            return context.getString(R.string.remaining_minutes, maxOf(1L, totalMinutes).toInt())
        }

        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        if (hours >= 24) return null

        return if (minutes > 0) {
            context.getString(R.string.remaining_hours_minutes, hours.toInt(), minutes.toInt())
        } else {
            context.getString(R.string.remaining_hours, hours.toInt())
        }
    }
}
