package com.example.sitdrinkreminder.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 本地配置中心。只用 SharedPreferences：不联网、不需要账号、不引入数据库依赖。
 *
 * 所有写操作使用 commit()：写入量极小，但能保证闹钟广播处理完之前数据一定落盘。
 */
class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ 提醒配置

    fun read(type: ReminderType): ReminderConfig {
        val suffix = type.prefSuffix
        val defaults = defaultsFor(type)
        val mode = when (prefs.getString(KEY_MODE + suffix, null)) {
            ScheduleMode.FIXED_TIME.name -> ScheduleMode.FIXED_TIME
            else -> ScheduleMode.INTERVAL
        }
        return ReminderConfig(
            type = type,
            enabled = prefs.getBoolean(KEY_ENABLED + suffix, false),
            mode = mode,
            intervalMinutes = ScheduleMath.coerceInterval(
                prefs.getInt(KEY_INTERVAL + suffix, defaults.first)
            ),
            timesOfDay = decodeTimes(prefs.getString(KEY_TIMES + suffix, null), defaults.second),
            nextTriggerAt = prefs.getLong(KEY_NEXT_AT + suffix, 0L)
        )
    }

    fun write(config: ReminderConfig) {
        val suffix = config.type.prefSuffix
        prefs.edit()
            .putBoolean(KEY_ENABLED + suffix, config.enabled)
            .putString(KEY_MODE + suffix, config.mode.name)
            .putInt(KEY_INTERVAL + suffix, ScheduleMath.coerceInterval(config.intervalMinutes))
            .putString(KEY_TIMES + suffix, encodeTimes(config.timesOfDay))
            .putLong(KEY_NEXT_AT + suffix, config.nextTriggerAt)
            .commit()
    }

    // ------------------------------------------------------------- 通知 / 权限偏好

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND, value).apply()
        }

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) {
            prefs.edit().putBoolean(KEY_VIBRATION, value).apply()
        }

    /** 用户是否希望使用“精确提醒”（真正能否精确还取决于系统是否授权） */
    var exactAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXACT_ALARM, true)
        set(value) {
            prefs.edit().putBoolean(KEY_EXACT_ALARM, value).apply()
        }

    /**
     * 上一次应用到通知频道上的参数签名。
     * Android 8.0+ 的声音/震动属于“频道”属性，改设置必须删掉频道重建；
     * 用签名比对可以只在我们自己的设置变化时才重建，不会覆盖用户在系统里的自定义。
     */
    var notificationSignature: String
        get() = prefs.getString(KEY_NOTIFICATION_SIGNATURE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_NOTIFICATION_SIGNATURE, value).apply()
        }

    /** 是否已经主动申请过通知权限，用于判断“不再询问”后直接跳系统设置 */
    var notificationAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, value).apply()
        }

    // ------------------------------------------------------------------ 提醒时段

    /** 提醒时段（免打扰窗口） */
    fun activeWindow(): ActiveWindow = ActiveWindow(
        enabled = prefs.getBoolean(KEY_WINDOW_ENABLED, false),
        startMinute = prefs.getInt(KEY_WINDOW_START, DEFAULT_WINDOW_START),
        endMinute = prefs.getInt(KEY_WINDOW_END, DEFAULT_WINDOW_END)
    )

    fun writeActiveWindow(window: ActiveWindow) {
        prefs.edit()
            .putBoolean(KEY_WINDOW_ENABLED, window.enabled)
            .putInt(KEY_WINDOW_START, window.startMinute)
            .putInt(KEY_WINDOW_END, window.endMinute)
            .commit()
    }

    // ---------------------------------------------------------------------- 内部

    private fun defaultsFor(type: ReminderType): Pair<Int, List<Int>> = when (type) {
        ReminderType.SIT -> 45 to listOf(9 * 60 + 30, 11 * 60, 14 * 60 + 30, 16 * 60 + 30)
        ReminderType.DRINK -> 60 to listOf(9 * 60, 11 * 60, 14 * 60, 16 * 60)
    }

    private fun encodeTimes(times: List<Int>): String =
        times.distinct().sorted().joinToString(separator = ",")

    /**
     * null 表示这个 key 还没写过（用默认值）；
     * 空字符串表示用户把时间点全删了（保持为空，不要回退默认值）。
     */
    private fun decodeTimes(raw: String?, fallback: List<Int>): List<Int> {
        if (raw == null) return fallback
        if (raw.isBlank()) return emptyList()
        return raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0 until 24 * 60 }
            .distinct()
            .sorted()
    }

    companion object {
        private const val PREFS_NAME = "sit_drink_reminder_settings"

        private const val KEY_ENABLED = "enabled_"
        private const val KEY_MODE = "mode_"
        private const val KEY_INTERVAL = "interval_"
        private const val KEY_TIMES = "times_"
        private const val KEY_NEXT_AT = "next_at_"

        private const val KEY_SOUND = "notification_sound"
        private const val KEY_VIBRATION = "notification_vibration"
        private const val KEY_EXACT_ALARM = "exact_alarm"
        private const val KEY_NOTIFICATION_SIGNATURE = "notification_signature"
        private const val KEY_NOTIFICATION_ASKED = "notification_asked"

        private const val KEY_WINDOW_ENABLED = "window_enabled"
        private const val KEY_WINDOW_START = "window_start"
        private const val KEY_WINDOW_END = "window_end"

        /** 默认提醒时段 08:00 - 22:00 */
        private const val DEFAULT_WINDOW_START = 8 * 60
        private const val DEFAULT_WINDOW_END = 22 * 60

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
    }
}
