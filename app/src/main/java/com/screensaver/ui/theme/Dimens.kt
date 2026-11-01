package com.screensaver.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 设计 token —— 间距。
 *
 * 全部基于 4dp 基准网格（4 / 8 / 12 / 16 / 20 / 24 / 32）。
 * 界面里所有 padding、gap、spacer 只从这里取值，不再散写硬编码数字，
 * 否则同样"看起来差不多"的 10.dp 与 12.dp 会在不同页面累积成视觉节奏漂移。
 */
object Space {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 20.dp
    val XXL = 24.dp

    /** 屏幕左右安全边距（对齐 DESIGN §3 space.xl）。 */
    val Screen = 20.dp
}

/**
 * 设计 token —— 圆角。
 *
 * 三档就够：小（控件内部元素）、中（卡片）、大（Hero / 弹窗）。
 * 档位越少，界面越像一个整体。
 */
object Radius {
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 20.dp
    val XXL = 28.dp
}

/**
 * 设计 token —— 尺寸。
 */
object Sizes {
    /** 段标题 / 状态图标所在的圆形徽标。 */
    val IconBadge = 32.dp
    /** 徽标内的图标。 */
    val IconS = 18.dp

    /** 时间条：列表内细条 / Hero 内粗条。 */
    val TimelineThin = 6.dp
    val TimelineThick = 10.dp
}
