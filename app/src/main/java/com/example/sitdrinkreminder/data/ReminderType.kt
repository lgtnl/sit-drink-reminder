package com.example.sitdrinkreminder.data

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.sitdrinkreminder.R

/**
 * 两类提醒。所有与类型相关的常量（通知 id、频道 id、闹钟 requestCode、文案）
 * 都集中在这里，新增第三种提醒时只需要加一个枚举值。
 */
enum class ReminderType(
    /** SharedPreferences key 后缀，例如 sit_enabled */
    val prefSuffix: String,
    /** PendingIntent 的 requestCode，必须全局唯一 */
    val requestCode: Int,
    /** 通知 id，两类提醒互不覆盖 */
    val notificationId: Int,
    /** NotificationChannel id（Android 8.0+ 必须） */
    val channelId: String,
    @StringRes val titleRes: Int,
    @StringRes val notificationTitleRes: Int,
    @StringRes val notificationBodyRes: Int,
    @StringRes val channelNameRes: Int,
    @DrawableRes val iconRes: Int,
    @ColorRes val accentColorRes: Int
) {
    SIT(
        prefSuffix = "sit",
        requestCode = 1001,
        notificationId = 1001,
        channelId = "reminder_sit",
        titleRes = R.string.title_sit,
        notificationTitleRes = R.string.notification_sit_title,
        notificationBodyRes = R.string.notification_sit_body,
        channelNameRes = R.string.channel_sit_name,
        iconRes = R.drawable.ic_walk,
        accentColorRes = R.color.brand_sit
    ),
    DRINK(
        prefSuffix = "drink",
        requestCode = 1002,
        notificationId = 1002,
        channelId = "reminder_drink",
        titleRes = R.string.title_drink,
        notificationTitleRes = R.string.notification_drink_title,
        notificationBodyRes = R.string.notification_drink_body,
        channelNameRes = R.string.channel_drink_name,
        iconRes = R.drawable.ic_drop,
        accentColorRes = R.color.brand_drink
    );

    companion object {
        /** 反序列化：广播/Intent 里只传枚举名字符串 */
        fun fromName(name: String?): ReminderType? =
            ReminderType.entries.firstOrNull { it.name == name }
    }
}
