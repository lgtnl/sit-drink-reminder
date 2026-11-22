package com.example.sitdrinkreminder.data

/**
 * 一类提醒的完整配置。不可变数据类，读取/修改都通过 [SettingsRepository] 完成。
 *
 * @param intervalMinutes 间隔模式的分钟数（5 ~ 1440）
 * @param timesOfDay      时间点模式的时间列表，元素是“当天第几分钟”，例如 09:30 -> 570
 * @param nextTriggerAt   下一次触发的时间戳（epoch millis），0 表示没有排期
 */
data class ReminderConfig(
    val type: ReminderType,
    val enabled: Boolean = false,
    val mode: ScheduleMode = ScheduleMode.INTERVAL,
    val intervalMinutes: Int = ScheduleMath.DEFAULT_INTERVAL_MINUTES,
    val timesOfDay: List<Int> = emptyList(),
    val nextTriggerAt: Long = 0L
)
