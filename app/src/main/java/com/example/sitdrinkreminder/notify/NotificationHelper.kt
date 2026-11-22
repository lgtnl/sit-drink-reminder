package com.example.sitdrinkreminder.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.sitdrinkreminder.R
import com.example.sitdrinkreminder.data.ReminderType
import com.example.sitdrinkreminder.data.SettingsRepository
import com.example.sitdrinkreminder.ui.MainActivity

/**
 * 通知相关：频道管理 + 发提醒通知。
 *
 * Android 8.0+ 的通知声音/震动是 **频道** 的属性，通知级别的 setSound 会被忽略，
 * 因此“声音/震动开关”要落到频道上：参数变化时删除旧频道再重建。
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    private val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val VIBRATION_PATTERN = longArrayOf(0L, 250L, 200L, 250L)

    /** 是否有权限发通知（Android 13+ 需要运行时授权，另外还要看用户是否整体关闭了通知） */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 保证通知频道存在，并在声音/震动设置变化时重建频道。
     * 只在我们自己的设置变化时重建，不覆盖用户在系统设置里对频道的自定义。
     */
    fun ensureChannels(context: Context, sound: Boolean, vibrate: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val repository = SettingsRepository.get(context)
        val signature = "sound=$sound;vibrate=$vibrate"
        val settingsAlreadyApplied = repository.notificationSignature == signature

        ReminderType.entries.forEach { type ->
            val existing = manager.getNotificationChannel(type.channelId)
            when {
                existing == null ->
                    manager.createNotificationChannel(createChannel(context, type, sound, vibrate))

                !settingsAlreadyApplied -> {
                    // 声音/震动只能在创建频道时指定，所以需要删掉重建
                    manager.deleteNotificationChannel(type.channelId)
                    manager.createNotificationChannel(createChannel(context, type, sound, vibrate))
                }
            }
        }
        repository.notificationSignature = signature
    }

    /** 发出一次提醒通知；点击通知打开 App。 */
    fun showReminder(context: Context, type: ReminderType) {
        if (!canPostNotifications(context)) {
            Log.w(TAG, "没有通知权限，跳过本次提醒：$type")
            return
        }
        val repository = SettingsRepository.get(context)
        ensureChannels(context, repository.soundEnabled, repository.vibrationEnabled)

        val title = context.getString(type.notificationTitleRes)
        val body = context.getString(type.notificationBodyRes)

        val contentIntent = PendingIntent.getActivity(
            context,
            type.requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                // 预留：通知被点击时告诉首页是哪一类提醒，方便二次开发做高亮/定位
                putExtra(MainActivity.EXTRA_FOCUS_TYPE, type.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, type.channelId)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(contentIntent)

        if (!repository.soundEnabled) {
            // Android 8.0+ 实际上由频道决定是否响铃，这里再兜一层（旧系统/厂商 ROM）
            builder.setSilent(true)
        }

        try {
            NotificationManagerCompat.from(context).notify(type.notificationId, builder.build())
        } catch (e: SecurityException) {
            // 极端情况：权限在检查之后被撤销
            Log.w(TAG, "发送通知失败（权限被撤销）", e)
        }
    }

    private fun createChannel(
        context: Context,
        type: ReminderType,
        sound: Boolean,
        vibrate: Boolean
    ): NotificationChannel =
        NotificationChannel(
            type.channelId,
            context.getString(type.channelNameRes),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(true)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            enableVibration(vibrate)
            if (vibrate) {
                vibrationPattern = VIBRATION_PATTERN
            }

            if (sound) {
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AUDIO_ATTRIBUTES)
            } else {
                setSound(null, null)
            }
        }
}
