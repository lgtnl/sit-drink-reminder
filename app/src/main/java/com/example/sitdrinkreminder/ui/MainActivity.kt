package com.example.sitdrinkreminder.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.sitdrinkreminder.R
import com.example.sitdrinkreminder.alarm.ReminderScheduler
import com.example.sitdrinkreminder.data.ReminderConfig
import com.example.sitdrinkreminder.data.ReminderType
import com.example.sitdrinkreminder.data.ScheduleMath
import com.example.sitdrinkreminder.data.SettingsRepository
import com.example.sitdrinkreminder.notify.NotificationHelper
import com.example.sitdrinkreminder.util.AppPermissions
import com.example.sitdrinkreminder.util.UiText

/**
 * 首页：两类提醒的开关与下次时间、提醒时段、通知/权限设置、权限缺失提示。
 * 使用平台 Activity 而非 AppCompatActivity，省掉 appcompat 依赖。
 */
class MainActivity : Activity() {

    private lateinit var repository: SettingsRepository
    private lateinit var sitCard: ReminderCardViews
    private lateinit var drinkCard: ReminderCardViews
    private lateinit var overview: TextView
    private lateinit var banner: View
    private lateinit var bannerText: TextView
    private lateinit var bannerAction: TextView
    private lateinit var windowSwitch: Switch
    private lateinit var windowSummary: TextView
    private lateinit var windowStartValue: TextView
    private lateinit var windowEndValue: TextView
    private lateinit var rowWindowStart: View
    private lateinit var rowWindowEnd: View
    private lateinit var soundSwitch: Switch
    private lateinit var vibrationSwitch: Switch
    private lateinit var exactSwitch: Switch
    private lateinit var exactStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var channelStatus: TextView
    private lateinit var fixChannelButton: TextView

    /** 代码回填控件状态时屏蔽监听器，避免“刷新 -> 触发保存 -> 再刷新”的死循环 */
    private var updatingWidgets = false
    private var bannerActionHandler: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repository = SettingsRepository.get(this)

        overview = findViewById(R.id.text_overview)
        banner = findViewById(R.id.banner)
        bannerText = findViewById(R.id.banner_text)
        bannerAction = findViewById(R.id.banner_action)
        windowSwitch = findViewById(R.id.switch_window)
        windowSummary = findViewById(R.id.text_window_summary)
        windowStartValue = findViewById(R.id.text_window_start)
        windowEndValue = findViewById(R.id.text_window_end)
        rowWindowStart = findViewById(R.id.row_window_start)
        rowWindowEnd = findViewById(R.id.row_window_end)
        soundSwitch = findViewById(R.id.switch_sound)
        vibrationSwitch = findViewById(R.id.switch_vibration)
        exactSwitch = findViewById(R.id.switch_exact)
        exactStatus = findViewById(R.id.text_exact_status)
        batteryStatus = findViewById(R.id.text_battery_status)
        channelStatus = findViewById(R.id.text_channel_status)
        fixChannelButton = findViewById(R.id.button_fix_channel)

        sitCard = ReminderCardViews.from(findViewById(R.id.card_sit))
        drinkCard = ReminderCardViews.from(findViewById(R.id.card_drink))

        bindCard(sitCard, ReminderType.SIT)
        bindCard(drinkCard, ReminderType.DRINK)
        bindWindow()
        bindNotificationSettings()
        showVersion()
    }

    override fun onResume() {
        super.onResume()
        // 用户可能刚从系统设置页回来（授权/撤销权限、改时间、改省电策略），对齐一次
        ReminderScheduler.resyncAll(this)
        refreshUi()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (NotificationHelper.hasUnhealthyChannel(this)) {
                // 授权之后系统可能仍把频道标记为静默，自动重建一次
                NotificationHelper.recreateChannels(this)
            } else {
                NotificationHelper.ensureChannels(
                    this,
                    repository.soundEnabled,
                    repository.vibrationEnabled
                )
            }
            refreshUi()
        }
    }

    // ------------------------------------------------------------------ 初始化

    private fun bindCard(card: ReminderCardViews, type: ReminderType) {
        card.icon.setImageResource(type.iconRes)
        card.icon.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, type.accentColorRes))
        card.title.text = getString(type.titleRes)
        card.toggle.contentDescription = getString(type.titleRes)

        card.toggle.setOnCheckedChangeListener { _, isChecked ->
            if (updatingWidgets) return@setOnCheckedChangeListener
            setReminderEnabled(type, isChecked)
        }
        card.edit.setOnClickListener { openReminderSettings(type) }
    }

    private fun bindWindow() {
        windowSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingWidgets) return@setOnCheckedChangeListener
            repository.writeActiveWindow(repository.activeWindow().copy(enabled = isChecked))
            ReminderScheduler.resyncAll(this)
            refreshUi()
        }
        rowWindowStart.setOnClickListener { pickWindowTime(start = true) }
        rowWindowEnd.setOnClickListener { pickWindowTime(start = false) }
    }

    private fun bindNotificationSettings() {
        banner.setOnClickListener { bannerActionHandler?.invoke() }
        bannerAction.setOnClickListener { bannerActionHandler?.invoke() }

        soundSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingWidgets) return@setOnCheckedChangeListener
            repository.soundEnabled = isChecked
            NotificationHelper.ensureChannels(
                this,
                repository.soundEnabled,
                repository.vibrationEnabled
            )
            refreshUi()
        }
        vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingWidgets) return@setOnCheckedChangeListener
            repository.vibrationEnabled = isChecked
            NotificationHelper.ensureChannels(
                this,
                repository.soundEnabled,
                repository.vibrationEnabled
            )
            refreshUi()
        }
        exactSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingWidgets) return@setOnCheckedChangeListener
            repository.exactAlarmEnabled = isChecked
            ReminderScheduler.resyncAll(this)
            if (isChecked && !AppPermissions.canScheduleExactAlarms(this)) {
                AppPermissions.openExactAlarmSettings(this)
            }
            refreshUi()
        }
        findViewById<View>(R.id.row_battery).setOnClickListener {
            AppPermissions.openBatteryOptimizationSettings(this)
        }
        fixChannelButton.setOnClickListener { fixChannels() }
        findViewById<View>(R.id.button_test_notification).setOnClickListener {
            sendTestNotification()
        }
    }

    private fun showVersion() {
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
        findViewById<TextView>(R.id.text_version).text =
            getString(R.string.footer_version, versionName)
    }

    // ------------------------------------------------------------------ 状态刷新

    private fun refreshUi() {
        updatingWidgets = true

        updateCard(sitCard, repository.read(ReminderType.SIT))
        updateCard(drinkCard, repository.read(ReminderType.DRINK))
        updateWindow()

        soundSwitch.isChecked = repository.soundEnabled
        vibrationSwitch.isChecked = repository.vibrationEnabled
        exactSwitch.isChecked = repository.exactAlarmEnabled
        exactStatus.text = exactStatusText()
        batteryStatus.text = if (AppPermissions.isIgnoringBatteryOptimizations(this)) {
            getString(R.string.battery_status_ok)
        } else {
            getString(R.string.battery_status_limited)
        }
        updateChannelStatus()

        updatingWidgets = false
        updateOverview()
        updateBanner()
    }

    private fun updateCard(card: ReminderCardViews, config: ReminderConfig) {
        card.toggle.isChecked = config.enabled

        val on = config.enabled
        card.status.text = getString(if (on) R.string.status_on else R.string.status_off)
        card.status.setBackgroundResource(if (on) R.drawable.bg_pill_on else R.drawable.bg_pill_off)
        card.status.setTextColor(
            ContextCompat.getColor(this, if (on) R.color.pill_on_text else R.color.pill_off_text)
        )

        card.summary.text = UiText.describeSchedule(this, config)
        card.next.text = when {
            !config.enabled -> getString(R.string.next_time_disabled)
            config.nextTriggerAt <= 0L -> getString(R.string.next_time_not_configured)
            else -> UiText.describeNextTrigger(this, config.nextTriggerAt)
        }
        card.setContentAlpha(if (on) 1f else 0.6f)
    }

    private fun updateWindow() {
        val window = repository.activeWindow()
        windowSwitch.isChecked = window.enabled
        windowStartValue.text = ScheduleMath.formatMinutesOfDay(window.startMinute)
        windowEndValue.text = ScheduleMath.formatMinutesOfDay(window.endMinute)
        windowSummary.text = when {
            !window.enabled -> getString(R.string.window_summary_off)
            window.startMinute == window.endMinute -> getString(R.string.window_summary_all_day)
            else -> getString(
                R.string.window_summary_on,
                ScheduleMath.formatMinutesOfDay(window.startMinute),
                ScheduleMath.formatMinutesOfDay(window.endMinute)
            )
        }
        val alpha = if (window.enabled) 1f else 0.45f
        rowWindowStart.alpha = alpha
        rowWindowEnd.alpha = alpha
    }

    /** 头部总览：显示两类提醒里最先到的那一个 */
    private fun updateOverview() {
        val now = System.currentTimeMillis()
        val enabled = ReminderType.entries
            .map { repository.read(it) }
            .filter { it.enabled && it.nextTriggerAt > 0L }
        val next = enabled.filter { it.nextTriggerAt > now }.minByOrNull { it.nextTriggerAt }

        overview.text = when {
            next != null -> getString(
                R.string.overview_format,
                getString(next.type.titleRes),
                UiText.describeMoment(this, next.nextTriggerAt, now)
            )

            enabled.isNotEmpty() -> getString(R.string.overview_soon)
            else -> getString(R.string.overview_none)
        }
    }

    private fun exactStatusText(): String {
        val permitted = AppPermissions.canScheduleExactAlarms(this)
        return when {
            !repository.exactAlarmEnabled -> getString(R.string.exact_status_off)
            permitted -> getString(R.string.exact_status_on)
            else -> getString(R.string.exact_status_no_permission)
        }
    }

    /**
     * 通知渠道自检。MIUI/HyperOS 会把新装 App 的通知默认设为“静默”：
     * 通知能进通知栏，但没有横幅/声音/震动。这种情况代码里改不了（importance 由系统定），
     * 必须让用户一键重建渠道或跳到系统设置里改。
     */
    private fun updateChannelStatus() {
        val healthy = !NotificationHelper.hasUnhealthyChannel(this)
        channelStatus.text = getString(
            if (healthy) R.string.channel_status_ok else R.string.channel_status_degraded
        )
        channelStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (healthy) R.color.text_secondary else R.color.banner_text
            )
        )
        fixChannelButton.visibility = if (healthy) View.GONE else View.VISIBLE
    }

    /**
     * 顶部提示条按“最影响提醒可靠性”的顺序只显示一条：
     * 通知权限 > 精确闹钟 > 电池优化。
     */
    private fun updateBanner() {
        val state: BannerState? = when {
            !NotificationHelper.canPostNotifications(this) -> BannerState(
                message = getString(R.string.banner_notification),
                actionLabel = getString(R.string.banner_notification_action)
            ) {
                AppPermissions.requestOrOpenNotificationSettings(this, REQUEST_NOTIFICATIONS)
            }

            !AppPermissions.canScheduleExactAlarms(this) -> BannerState(
                message = getString(R.string.banner_exact),
                actionLabel = getString(R.string.banner_exact_action)
            ) {
                AppPermissions.openExactAlarmSettings(this)
            }

            !AppPermissions.isIgnoringBatteryOptimizations(this) -> BannerState(
                message = getString(R.string.banner_battery),
                actionLabel = getString(R.string.banner_battery_action)
            ) {
                AppPermissions.openBatteryOptimizationSettings(this)
            }

            else -> null
        }

        if (state == null) {
            banner.visibility = View.GONE
            bannerActionHandler = null
            return
        }
        banner.visibility = View.VISIBLE
        bannerText.text = state.message
        bannerAction.text = state.actionLabel
        bannerActionHandler = state.onAction
    }

    // ------------------------------------------------------------------ 交互

    private fun setReminderEnabled(type: ReminderType, enabled: Boolean) {
        val base = repository.read(type).copy(enabled = enabled)
        val now = System.currentTimeMillis()
        val next = if (enabled) {
            ReminderScheduler.nextTriggerFor(
                base,
                now,
                keepExistingIfFuture = false,
                window = repository.activeWindow()
            )
        } else {
            0L
        }
        val updated = base.copy(nextTriggerAt = next)
        repository.write(updated)
        ReminderScheduler.scheduleConfig(this, updated)

        if (enabled) {
            AppPermissions.requestOrOpenNotificationSettings(this, REQUEST_NOTIFICATIONS)
        }
        refreshUi()

        // 时间点模式下一个时间点都没有时，直接带用户去补时间，避免“开了却不会提醒”
        if (enabled && next <= 0L) {
            openReminderSettings(type)
        }
    }

    private fun pickWindowTime(start: Boolean) {
        val window = repository.activeWindow()
        val current = if (start) window.startMinute else window.endMinute
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val value = hourOfDay * 60 + minute
                val updated = if (start) {
                    window.copy(startMinute = value)
                } else {
                    window.copy(endMinute = value)
                }
                repository.writeActiveWindow(updated)
                ReminderScheduler.resyncAll(this)
                refreshUi()
            },
            current / 60,
            current % 60,
            true
        ).show()
    }

    private fun fixChannels() {
        val recovered = NotificationHelper.recreateChannels(this)
        refreshUi()
        if (!recovered) {
            // 重建后仍然静默 → 是系统级开关/厂商策略，只能去系统设置里改
            AppPermissions.openChannelSettings(this, ReminderType.SIT.channelId)
        }
    }

    private fun sendTestNotification() {
        if (!NotificationHelper.canPostNotifications(this)) {
            AppPermissions.requestOrOpenNotificationSettings(this, REQUEST_NOTIFICATIONS)
            return
        }
        NotificationHelper.showReminder(this, ReminderType.SIT)
        refreshUi()
    }

    private fun openReminderSettings(type: ReminderType) {
        startActivity(
            Intent(this, ReminderSettingsActivity::class.java)
                .putExtra(ReminderSettingsActivity.EXTRA_TYPE, type.name)
        )
    }

    private data class BannerState(
        val message: String,
        val actionLabel: String,
        val onAction: () -> Unit
    )

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1001

        /** 通知点击时携带，预留给二次开发做定位/高亮 */
        const val EXTRA_FOCUS_TYPE = "extra_focus_type"
    }
}
