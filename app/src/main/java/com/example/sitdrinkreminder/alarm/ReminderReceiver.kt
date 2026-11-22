package com.example.sitdrinkreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.sitdrinkreminder.data.ReminderType
import com.example.sitdrinkreminder.data.SettingsRepository
import com.example.sitdrinkreminder.notify.NotificationHelper

/**
 * 闹钟到点的接收器，另外负责通知上的「稍后提醒」按钮。
 *
 * 提醒链路：排下一次 -> 发通知。耗时只有“读写 SharedPreferences + 注册一个闹钟 + 发一条通知”，
 * 都是同步的轻量操作，所以不需要 goAsync() 或 WakeLock，Receiver 可以很快返回。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMIND -> handleRemind(context, intent)
            ACTION_SNOOZE -> handleSnooze(context, intent)
            else -> Unit
        }
    }

    /** 到点：先排下一次，再发通知 */
    private fun handleRemind(context: Context, intent: Intent) {
        val type = ReminderType.fromName(intent.getStringExtra(EXTRA_REMINDER_TYPE)) ?: return

        val repository = SettingsRepository.get(context)
        val config = repository.read(type)
        if (!config.enabled) {
            // 用户在闹钟触发前关掉了提醒：撤销残留闹钟即可
            Log.d(TAG, "$type 已关闭，忽略本次触发")
            ReminderScheduler.cancel(context, type)
            return
        }

        // 1) 先排下一次，保证即使发通知出错，提醒链也不会断
        val now = System.currentTimeMillis()
        val next = ReminderScheduler.nextTriggerFor(
            config,
            now,
            keepExistingIfFuture = false,
            window = repository.activeWindow()
        )
        val updated = config.copy(nextTriggerAt = next)
        repository.write(updated)
        ReminderScheduler.scheduleConfig(context, updated)

        // 2) 再发通知（即使发通知失败也不影响下一次提醒）
        try {
            NotificationHelper.showReminder(context, type)
        } catch (e: Exception) {
            Log.e(TAG, "发送提醒通知失败", e)
        }
    }

    /** 用户点了通知上的「稍后 N 分钟」：把下一次触发推迟，并撤掉当前通知 */
    private fun handleSnooze(context: Context, intent: Intent) {
        val type = ReminderType.fromName(intent.getStringExtra(EXTRA_REMINDER_TYPE)) ?: return
        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)
            .coerceIn(1, MAX_SNOOZE_MINUTES)

        val repository = SettingsRepository.get(context)
        val config = repository.read(type)
        if (!config.enabled) {
            NotificationHelper.cancel(context, type)
            return
        }

        val snoozedUntil = System.currentTimeMillis() + minutes * 60_000L
        val updated = config.copy(nextTriggerAt = snoozedUntil)
        repository.write(updated)
        ReminderScheduler.scheduleConfig(context, updated)
        NotificationHelper.cancel(context, type)
        Log.d(TAG, "$type 已推迟 $minutes 分钟")
    }

    companion object {
        private const val TAG = "ReminderReceiver"

        const val ACTION_REMIND = "com.example.sitdrinkreminder.action.REMIND"
        const val ACTION_SNOOZE = "com.example.sitdrinkreminder.action.SNOOZE"

        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"

        /** 通知上「稍后」按钮推迟的分钟数 */
        const val DEFAULT_SNOOZE_MINUTES = 10
        private const val MAX_SNOOZE_MINUTES = 180
    }
}
