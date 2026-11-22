package com.example.sitdrinkreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.sitdrinkreminder.data.ActiveWindow
import com.example.sitdrinkreminder.data.ReminderConfig
import com.example.sitdrinkreminder.data.ReminderType
import com.example.sitdrinkreminder.data.ScheduleMath
import com.example.sitdrinkreminder.data.ScheduleMode
import com.example.sitdrinkreminder.data.SettingsRepository
import com.example.sitdrinkreminder.util.AppPermissions
import java.time.ZoneId

/**
 * 提醒调度器（AlarmManager 方案）。
 *
 * 设计要点：
 * 1. 每类提醒只挂 **一个** 闹钟，触发后在 Receiver 里再排下一个 —— 即“自调度链”。
 *    好处：开机/改设置时只需要重算一次下次时间，不用维护一堆 PendingIntent。
 * 2. 优先精确：`setExactAndAllowWhileIdle`（Android 12+ 需要用户授权精确闹钟）。
 *    没有授权时自动降级为 `setAndAllowWhileIdle`，仍然能在 Doze 下触发，但可能延迟几分钟。
 * 3. 全部使用 RTC_WAKEUP，设备休眠时也会唤醒 CPU 发通知。
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    private const val SCHEME = "sitdrink"
    private const val HOST = "reminder"

    /**
     * 计算下一次触发时间（epoch millis），0 表示不需要排闹钟。
     *
     * @param keepExistingIfFuture true：沿用配置里已有的时间（开机/回前台自愈用；
     *                             仍在未来就保留，刚错过就补发，错过太久则重新计时）。
     *                             false：从现在重新计时（用户刚修改了设置）。
     * @param window 提醒时段；落在时段外的时间会被顺延到下一个时段开始。
     */
    fun nextTriggerFor(
        config: ReminderConfig,
        nowMillis: Long = System.currentTimeMillis(),
        keepExistingIfFuture: Boolean,
        window: ActiveWindow = ActiveWindow.ALWAYS
    ): Long {
        if (!config.enabled) return 0L
        val zone = ZoneId.systemDefault()
        return when (config.mode) {
            ScheduleMode.INTERVAL -> {
                val raw = if (keepExistingIfFuture) {
                    ScheduleMath.ensureFuture(config.nextTriggerAt, config.intervalMinutes, nowMillis)
                } else {
                    ScheduleMath.firstTriggerFromNow(config.intervalMinutes, nowMillis)
                }
                ScheduleMath.clampToWindow(raw, window, zone)
            }

            ScheduleMode.FIXED_TIME -> {
                val computed = ScheduleMath.nextFixedOccurrence(config.timesOfDay, nowMillis, zone)
                // 「稍后提醒」把 nextTriggerAt 设成了未来更早的时间点 —— 用户显式要求，不受时段限制
                val snoozed = config.nextTriggerAt > nowMillis &&
                    (computed == null || config.nextTriggerAt < computed)
                when {
                    keepExistingIfFuture && snoozed -> config.nextTriggerAt
                    computed == null -> 0L
                    else -> ScheduleMath.clampToWindow(computed, window, zone)
                }
            }
        }
    }

    /** 注册（或取消）某一类提醒的闹钟。config.enabled=false 或没有下次时间时会取消。 */
    fun scheduleConfig(context: Context, config: ReminderConfig) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(context, config.type)

        // 先撤掉旧的，保证同一类提醒永远只有一个闹钟
        alarmManager.cancel(pendingIntent)

        if (!config.enabled || config.nextTriggerAt <= 0L) {
            Log.d(TAG, "${config.type} 未启用或没有下次时间，不排闹钟")
            return
        }

        val triggerAt = config.nextTriggerAt
        val exact = isExactSchedulingActive(context)
        try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Log.d(TAG, "${config.type} 已排闹钟 exact=$exact at=$triggerAt")
        } catch (e: SecurityException) {
            // Android 12+：授权在检查之后被撤销时会抛异常，降级重试
            Log.w(TAG, "精确闹钟被系统拒绝，降级为非精确闹钟", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, type: ReminderType) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(buildPendingIntent(context, type))
    }

    /**
     * 全量重新同步：开机、覆盖安装、改系统时间、时区变化、权限变化、App 启动时调用。
     * 会顺带把 prefs 里的 nextTriggerAt 修正到最新值。
     */
    fun resyncAll(context: Context) {
        val repository = SettingsRepository.get(context)
        val window = repository.activeWindow()
        ReminderType.entries.forEach { type ->
            val config = repository.read(type)
            val now = System.currentTimeMillis()
            val next = nextTriggerFor(config, now, keepExistingIfFuture = true, window = window)
            val updated = config.copy(nextTriggerAt = next)
            if (updated != config) {
                // 关机期间错过、或时间点已过期：把修正后的时间写回，保证首页显示准确
                repository.write(updated)
            }
            scheduleConfig(context, updated)
        }
    }

    /** 当前是否真的会使用精确闹钟（用户没关 + 系统已授权） */
    fun isExactSchedulingActive(context: Context): Boolean =
        SettingsRepository.get(context).exactAlarmEnabled &&
            AppPermissions.canScheduleExactAlarms(context)

    /**
     * 用 data URI 区分两类提醒的 PendingIntent，
     * 这样即使 requestCode 冲突也不会互相覆盖。
     */
    private fun buildPendingIntent(context: Context, type: ReminderType): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            data = Uri.parse("$SCHEME://$HOST/${type.name}")
            putExtra(ReminderReceiver.EXTRA_REMINDER_TYPE, type.name)
        }
        return PendingIntent.getBroadcast(
            context,
            type.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
