package com.example.sitdrinkreminder.ui

import android.app.Activity
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
import com.example.sitdrinkreminder.data.SettingsRepository
import com.example.sitdrinkreminder.notify.NotificationHelper
import com.example.sitdrinkreminder.util.AppPermissions
import com.example.sitdrinkreminder.util.UiText

/**
 * 首页：两类提醒的开关 + 下次提醒时间，通知/权限设置，权限缺失提示。
 * 故意使用平台 Activity 而非 AppCompatActivity，省掉 appcompat 依赖。
 */
class MainActivity : Activity() {

    private lateinit var repository: SettingsRepository
    private lateinit var sitCard: ReminderCardViews
    private lateinit var drinkCard: ReminderCardViews
    private lateinit var banner: View
    private lateinit var bannerText: TextView
    private lateinit var bannerAction: TextView
    private lateinit var soundSwitch: Switch
    private lateinit var vibrationSwitch: Switch
    private lateinit var exactSwitch: Switch
    private lateinit var exactStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var channelStatus: TextView
    private lateinit var fixChannelButton: TextView

    /** 代码回填控件状态时，屏蔽监听器，避免“刷新 -> 触发保存 -> 再刷新”的死循环 */
    private var updatingWidgets = false
    private var bannerActionHandler: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repository = SettingsRepository.get(this)

        banner = findViewById(R.id.banner)
        bannerText = findViewById(R.id.banner_text)
        bannerAction = findViewById(R.id.banner_action)
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
        }
        vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingWidgets) return@setOnCheckedChangeListener
            repository.vibrationEnabled = isChecked
            NotificationHelper.ensureChannels(
                this,
                repository.soundEnabled,
                repository.vibrationEnabled
            )
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
        updateBanner()
    }

    /**
     * 通知渠道自检。MIUI/HyperOS 会把新装 App 的通知默认设为“静默”：
     * 通知能进通知栏，但没有横幅/声音/震动。这种情况代码里换不了（importance 由系统定），
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

    private fun updateCard(card: ReminderCardViews, config: ReminderConfig) {
        card.toggle.isChecked = config.enabled
        card.summary.text = UiText.describeSchedule(this, config)
        card.next.text = when {
            !config.enabled -> getString(R.string.next_time_disabled)
            config.nextTriggerAt <= 0L -> getString(R.string.next_time_not_configured)
            else -> UiText.describeNextTrigger(this, config.nextTriggerAt)
        }
        card.setContentAlpha(if (config.enabled) 1f else 0.6f)
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
            ReminderScheduler.nextTriggerFor(base, now, keepExistingIfFuture = false)
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
