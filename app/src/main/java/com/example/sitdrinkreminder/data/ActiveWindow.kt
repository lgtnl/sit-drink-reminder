package com.example.sitdrinkreminder.data

/**
 * 提醒时段（免打扰窗口）。全局生效，两类提醒共用。
 *
 * - [startMinute] / [endMinute] 是“当天第几分钟”，例如 08:00 -> 480
 * - 允许跨夜：start > end 表示 22:00 到次日 06:00
 * - start == end 视为“全天不限制”
 */
data class ActiveWindow(
    val enabled: Boolean,
    val startMinute: Int,
    val endMinute: Int
) {
    /** 是否真的需要做时段裁剪 */
    val isRestricted: Boolean
        get() = enabled && startMinute.coerceIn(0, 24 * 60 - 1) != endMinute.coerceIn(0, 24 * 60 - 1)

    companion object {
        val ALWAYS = ActiveWindow(enabled = false, startMinute = 8 * 60, endMinute = 22 * 60)
    }
}
