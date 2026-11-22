package com.example.sitdrinkreminder.ui

import android.view.View
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import com.example.sitdrinkreminder.R

/**
 * 首页一张提醒卡片的控件引用。
 * 两种提醒共用同一个布局（view_reminder_card.xml），只是标题/图标/颜色/状态不同。
 */
internal class ReminderCardViews private constructor(
    val icon: ImageView,
    val title: TextView,
    val status: TextView,
    val next: TextView,
    val summary: TextView,
    val toggle: Switch,
    val edit: View
) {

    /** 关闭状态下弱化次要信息 */
    fun setContentAlpha(alpha: Float) {
        icon.alpha = alpha
        next.alpha = alpha
        summary.alpha = alpha
    }

    companion object {
        fun from(root: View): ReminderCardViews = ReminderCardViews(
            icon = root.findViewById(R.id.card_icon),
            title = root.findViewById(R.id.card_title),
            status = root.findViewById(R.id.card_status),
            next = root.findViewById(R.id.card_next),
            summary = root.findViewById(R.id.card_summary),
            toggle = root.findViewById(R.id.card_toggle),
            edit = root.findViewById(R.id.card_edit)
        )
    }
}
