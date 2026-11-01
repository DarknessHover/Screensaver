package com.screensaver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 品牌渐变。
 *
 * **只给大面积品牌色块用**：状态 Hero 的生效态、空态的圆形图标底（64dp）。
 * FAB / Button / FilterChip 等小控件一律纯色 —— 56dp 高的控件读不出渐变只会显脏；
 * 给 Material 组件设置透明容器再自绘渐变时，`Surface` 的 tonalElevation 覆盖层会污染颜色。
 * 中性表面（页面、卡片、顶栏）一律纯色 —— 渐变是身份标识而不是通用装饰，
 * 到处贴等于没有重点，也会让卡片层级失去参照系。
 *
 * 两端刻意选了**明度接近、色相不同**的一对（ΔL < 0.015）：
 *  - 渐变读作"颜色在流动"，而不是"一头亮一头暗"；
 *  - 副作用是文字压在渐变任何位置，对比度都一致。白字实测 5.7–7.2:1，全过 WCAG AA。
 * 如果换成明度跨度大的渐变（如 primary → primaryContainer），
 * 深色那头的正文会掉到 3:1 以下，所以别那么选。
 */
object BrandGradient {

    private val LightStops = listOf(
        Color(0xFF3A5BDB),   // 品牌蓝（与 colors.xml 的 brand_primary 一致）
        Color(0xFF6B4BD1),   // 蓝紫
    )

    private val DarkStops = listOf(
        Color(0xFF3D55B8),   // 深色下压暗一档，避免在深背景上发荧光
        Color(0xFF5B3FBF),
    )

    /**
     * 左上 → 右下的线性渐变。
     *
     * 明暗判断取自当前 colorScheme 的 surface 明度，因此与 `ScreensaverTheme`
     * 实际采用的方案永远一致（不依赖 isSystemInDarkTheme 的二次读取）。
     */
    @Composable
    fun hero(): Brush {
        val stops = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
            LightStops
        } else {
            DarkStops
        }
        return Brush.linearGradient(stops)
    }
}
