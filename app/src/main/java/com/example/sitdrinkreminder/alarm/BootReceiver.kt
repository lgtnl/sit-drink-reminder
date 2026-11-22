package com.example.sitdrinkreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 系统事件接收器：重新恢复提醒计划。
 *
 * - BOOT_COMPLETED：重启后 AlarmManager 里所有闹钟都会被清空，必须重排
 * - MY_PACKAGE_REPLACED：覆盖安装后同样需要重排
 * - TIME_SET / TIMEZONE_CHANGED：时间点模式下需要按新时区重算
 * - SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED：精确闹钟权限变化后立即用精确模式重排
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                Log.d(TAG, "收到 $action，重新同步提醒计划")
                ReminderScheduler.resyncAll(context)
            }

            else -> Unit
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        /** Android 12+：精确闹钟权限被授予/撤销时系统发出的广播 */
        const val ACTION_EXACT_ALARM_PERMISSION_STATE_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
