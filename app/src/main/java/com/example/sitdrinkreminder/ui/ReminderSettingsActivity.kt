package com.example.sitdrinkreminder.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.sitdrinkreminder.R
import com.example.sitdrinkreminder.alarm.ReminderScheduler
import com.example.sitdrinkreminder.data.ReminderConfig
import com.example.sitdrinkreminder.data.ReminderType
import com.example.sitdrinkreminder.data.ScheduleMath
import com.example.sitdrinkreminder.data.ScheduleMode
import com.example.sitdrinkreminder.data.SettingsRepository
import java.time.LocalTime

/**
 * 单类提醒的设置页：开关、提醒方式（间隔 / 时间点）、间隔选择（含自定义）、时间点增删。
 *
 * 交互原则：**改动即生效**，没有“保存”按钮，减少状态不一致的可能。
 * 每次改动都会重新计算下次触发时间（间隔模式从现在重新计时，并受“提醒时段”约束）。
 */
class ReminderSettingsActivity : Activity() {

    private lateinit var repository: SettingsRepository
    private lateinit var type: ReminderType

    private lateinit var enabledSwitch: Switch
    private lateinit var modeGroup: RadioGroup
    private lateinit var intervalRadio: RadioButton
    private lateinit var fixedRadio: RadioButton
    private lateinit var intervalSection: View
    private lateinit var fixedSection: View
    private lateinit var intervalValue: TextView
    private lateinit var intervalWarning: TextView
    private lateinit var timesContainer: LinearLayout
    private lateinit var timesEmpty: TextView

    private var updatingWidgets = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reminderType = ReminderType.fromName(intent.getStringExtra(EXTRA_TYPE))
        if (reminderType == null) {
            finish()
            return
        }
        type = reminderType
        repository = SettingsRepository.get(this)
        setContentView(R.layout.activity_reminder_settings)

        enabledSwitch = findViewById(R.id.switch_reminder_enabled)
        modeGroup = findViewById(R.id.mode_group)
        intervalRadio = findViewById(R.id.radio_interval)
        fixedRadio = findViewById(R.id.radio_fixed)
        intervalSection = findViewById(R.id.section_interval)
        fixedSection = findViewById(R.id.section_fixed)
        intervalValue = findViewById(R.id.text_interval_value)
        intervalWarning = findViewById(R.id.text_interval_warning)
        timesContainer = findViewById(R.id.container_times)
        timesEmpty = findViewById(R.id.text_times_empty)

        findViewById<TextView>(R.id.text_screen_title).text =
            getString(R.string.settings_title, getString(type.titleRes))

        findViewById<View>(R.id.button_back).setOnClickListener { finish() }
        findViewById<View>(R.id.button_done).setOnClickListener { finish() }
        findViewById<View>(R.id.row_interval).setOnClickListener { showIntervalPicker() }
        findViewById<View>(R.id.button_add_time).setOnClickListener { showTimePicker() }

        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingWidgets) return@setOnCheckedChangeListener
            persist { it.copy(enabled = isChecked) }
        }
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (updatingWidgets) return@setOnCheckedChangeListener
            val mode = if (checkedId == R.id.radio_fixed) {
                ScheduleMode.FIXED_TIME
            } else {
                ScheduleMode.INTERVAL
            }
            persist { it.copy(mode = mode) }
        }

        refreshUi()
    }

    // ------------------------------------------------------------------ 状态刷新

    private fun refreshUi() {
        updatingWidgets = true
        val config = repository.read(type)

        enabledSwitch.isChecked = config.enabled
        if (config.mode == ScheduleMode.FIXED_TIME) {
            fixedRadio.isChecked = true
        } else {
            intervalRadio.isChecked = true
        }
        intervalValue.text = getString(R.string.interval_value_format, config.intervalMinutes)
        intervalWarning.visibility = if (
            config.mode == ScheduleMode.INTERVAL &&
            config.intervalMinutes < WARNING_INTERVAL_THRESHOLD
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }

        intervalSection.visibility =
            if (config.mode == ScheduleMode.INTERVAL) View.VISIBLE else View.GONE
        fixedSection.visibility =
            if (config.mode == ScheduleMode.FIXED_TIME) View.VISIBLE else View.GONE

        renderTimes(config.timesOfDay)

        updatingWidgets = false
    }

    private fun renderTimes(times: List<Int>) {
        timesContainer.removeAllViews()
        timesEmpty.visibility = if (times.isEmpty()) View.VISIBLE else View.GONE

        times.sorted().forEach { minutesOfDay ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_time_row, timesContainer, false)
            row.findViewById<TextView>(R.id.text_time_value).text =
                ScheduleMath.formatMinutesOfDay(minutesOfDay)
            row.findViewById<View>(R.id.button_remove_time).setOnClickListener {
                persist { config ->
                    config.copy(timesOfDay = config.timesOfDay.filter { it != minutesOfDay })
                }
            }
            timesContainer.addView(row)
        }
    }

    // ------------------------------------------------------------------ 保存

    /**
     * 统一出口：规范化 -> 重新计算下次触发时间 -> 落盘 -> 排闹钟 -> 刷新界面。
     * 任何配置改动都要走这里，避免漏排闹钟。
     */
    private fun persist(change: (ReminderConfig) -> ReminderConfig) {
        val normalized = change(repository.read(type)).let { config ->
            config.copy(
                intervalMinutes = ScheduleMath.coerceInterval(config.intervalMinutes),
                timesOfDay = config.timesOfDay.distinct().sorted()
            )
        }

        val now = System.currentTimeMillis()
        val next = if (normalized.enabled) {
            ReminderScheduler.nextTriggerFor(
                normalized,
                now,
                keepExistingIfFuture = false,
                window = repository.activeWindow()
            )
        } else {
            0L
        }

        val updated = normalized.copy(nextTriggerAt = next)
        repository.write(updated)
        ReminderScheduler.scheduleConfig(this, updated)
        refreshUi()
    }

    // ------------------------------------------------------------------ 弹窗

    private fun showIntervalPicker() {
        val current = repository.read(type).intervalMinutes
        val presets = ScheduleMath.INTERVAL_PRESETS.toMutableList()
        if (!presets.contains(current)) {
            presets.add(current)
            presets.sort()
        }

        val labels = (
            presets.map { getString(R.string.interval_value_format, it) } +
                getString(R.string.interval_custom)
            ).toTypedArray()
        val checkedIndex = presets.indexOf(current)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_interval_title)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                dialog.dismiss()
                if (which == presets.size) {
                    showCustomIntervalDialog()
                } else {
                    val minutes = presets[which]
                    persist { it.copy(intervalMinutes = minutes) }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** 自定义间隔：允许 1~1440 分钟，例如 1、2、10 分钟 */
    private fun showCustomIntervalDialog() {
        val current = repository.read(type).intervalMinutes
        val density = resources.displayMetrics.density

        val input = EditText(this).apply {
            setText(current.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSelection(text.length)
            hint = getString(R.string.dialog_custom_interval_hint)
            val pad = (14 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val container = FrameLayout(this).apply {
            val pad = (20 * density).toInt()
            setPadding(pad, (8 * density).toInt(), pad, 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_custom_interval_title)
            .setView(container)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                val minutes = input.text.toString().trim().toIntOrNull()
                if (minutes == null ||
                    minutes < ScheduleMath.MIN_INTERVAL_MINUTES ||
                    minutes > ScheduleMath.MAX_INTERVAL_MINUTES
                ) {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.interval_invalid,
                            ScheduleMath.MIN_INTERVAL_MINUTES,
                            ScheduleMath.MAX_INTERVAL_MINUTES
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    persist { it.copy(intervalMinutes = ScheduleMath.coerceInterval(minutes)) }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showTimePicker() {
        val config = repository.read(type)
        val suggestedMinutes = config.timesOfDay.maxOrNull()
            ?.let { (it + 60).coerceAtMost(23 * 60 + 59) }
            ?: LocalTime.now().plusHours(1).let { it.hour * 60 + it.minute }

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val minutesOfDay = hourOfDay * 60 + minute
                persist { it.copy(timesOfDay = it.timesOfDay + minutesOfDay) }
            },
            suggestedMinutes / 60,
            suggestedMinutes % 60,
            true
        ).show()
    }

    companion object {
        const val EXTRA_TYPE = "extra_reminder_type"

        /** 低于这个间隔时提示“可能被系统合并” */
        private const val WARNING_INTERVAL_THRESHOLD = 5
    }
}
