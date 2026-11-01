package com.screensaver.ui.util

import java.util.Calendar

/** 分钟数(0..1440) → "HH:MM"（1440 显示为 24:00）。 */
fun formatMinute(minute: Int): String {
    val m = minute.coerceIn(0, 1440)
    val h = m / 60
    val mm = m % 60
    return "%02d:%02d".format(h, mm)
}

/**
 * 解析 24 小时制 HH:mm。
 * [allowEndOfDay] 为 true 时额外允许 24:00，供规则结束时间表达一天末尾。
 */
fun parseMinute(text: String, allowEndOfDay: Boolean = false): Int? {
    val parts = text.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (allowEndOfDay && hour == 24 && minute == 0) return 1440
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

/** 当前“当天第几分钟”。 */
fun nowMinuteOfDay(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

/** 时长（分钟）→ 中文可读文本，如 210 → "3 小时 30 分"、45 → "45 分钟"。 */
fun formatDuration(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    val h = m / 60
    val mm = m % 60
    return when {
        h == 0 -> "$mm 分钟"
        mm == 0 -> "$h 小时"
        else -> "$h 小时 $mm 分"
    }
}
