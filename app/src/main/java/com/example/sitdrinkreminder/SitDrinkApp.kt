package com.example.sitdrinkreminder

import android.app.Application
import com.example.sitdrinkreminder.alarm.ReminderScheduler
import com.example.sitdrinkreminder.data.SettingsRepository
import com.example.sitdrinkreminder.notify.NotificationHelper

/**
 * Application 入口。做过两件事：
 * 1. 保证通知频道存在（Android 8.0+ 不建频道就发不出通知）
 * 2. 启动时自愈式重排闹钟 —— 被“强行停止”过的 App 闹钟会被系统清空，
 *    用户再次打开 App 时提醒能自动恢复
 */
class SitDrinkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val repository = SettingsRepository.get(this)
        NotificationHelper.ensureChannels(
            this,
            repository.soundEnabled,
            repository.vibrationEnabled
        )
        ReminderScheduler.resyncAll(this)
    }
}
