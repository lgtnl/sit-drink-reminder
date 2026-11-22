package com.example.sitdrinkreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.sitdrinkreminder.data.ReminderType
import com.example.sitdrinkreminder.data.SettingsRepository
import com.example.sitdrinkreminder.notify.NotificationHelper

/**
 * 闹钟到点的接收器：排下一次 -> 发通知。
 *
 * 这里的耗时只有“读写 SharedPreferences + 注册一个闹钟 + 发一条通知”，
 * 都是同步的轻量操作，所以不需要 goAsync() 或 WakeLock，Receiver 可以很快返回。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
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
        val next = ReminderScheduler.nextTriggerFor(config, now, keepExistingIfFuture = false)
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

    companion object {
        private const val TAG = "ReminderReceiver"

        const val ACTION_REMIND = "com.example.sitdrinkreminder.action.REMIND"
        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
    }
}
