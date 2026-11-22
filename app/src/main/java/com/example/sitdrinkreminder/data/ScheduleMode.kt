package com.example.sitdrinkreminder.data

/**
 * 提醒方式：
 * - [INTERVAL]   每隔 N 分钟提醒一次（从开启/修改设置的那一刻开始计时）
 * - [FIXED_TIME] 每天在固定的若干个时间点提醒
 */
enum class ScheduleMode {
    INTERVAL,
    FIXED_TIME
}
