package com.example.sitdrinkreminder.util

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sitdrinkreminder.data.SettingsRepository

/**
 * 权限与系统设置的统一入口。
 *
 * 覆盖 Android 12/13/14 的行为变化：
 * - 12（31）：精确闹钟需要 SCHEDULE_EXACT_ALARM，用户可关闭
 * - 13（33）：通知需要 POST_NOTIFICATIONS 运行时权限
 * - 14（34）：精确闹钟默认不授予（新安装），必须引导用户手动授权
 */
object AppPermissions {

    /** Android 13+ 的通知运行时权限；13 以下恒为 true */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 申请通知权限；如果用户已经拒绝过两次（系统不再弹窗），
     * 则直接跳到本 App 的通知设置页，避免“点了没反应”。
     */
    fun requestOrOpenNotificationSettings(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            openNotificationSettings(activity)
            return
        }
        if (hasNotificationPermission(activity)) return

        val repository = SettingsRepository.get(activity)
        val canAskAgain = !repository.notificationAsked ||
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        if (canAskAgain) {
            repository.notificationAsked = true
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestCode
            )
        } else {
            openNotificationSettings(activity)
        }
    }

    /** Android 12+ 是否被允许使用精确闹钟（12 以下恒为 true） */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    /** 是否已加入“不受电池优化限制”（Doze 下更可靠） */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startSafely(context, intent)
    }

    /**
     * 直接打开某个通知频道的系统设置页（横幅/声音/震动都在这一页），
     * 系统把它降级成静默时，用户需要在这里改。
     */
    fun openChannelSettings(context: Context, channelId: String) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!tryStart(context, intent)) {
            openNotificationSettings(context)
        }
    }

    /** 打开系统的“闹钟和提醒”授权页（Android 12+），失败则退回应用详情页 */
    fun openExactAlarmSettings(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!tryStart(context, intent)) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * 打开电池优化列表（不需要任何敏感权限，也不会被应用商店判定为诱导）。
     * 用户可以在里面把本 App 设为“不限制”。
     */
    fun openBatteryOptimizationSettings(context: Context) {
        startSafely(
            context,
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openAppDetailsSettings(context: Context) {
        startSafely(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * 部分定制 ROM 可能缺少某些系统设置页，这里做两级兜底，避免直接崩溃。
     */
    private fun startSafely(context: Context, intent: Intent) {
        if (!tryStart(context, intent)) {
            tryStart(
                context,
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (ignored: Exception) {
        false
    }
}
