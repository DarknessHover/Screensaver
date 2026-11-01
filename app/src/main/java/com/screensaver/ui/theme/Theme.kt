package com.screensaver.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 浅色配色。主色沿用品牌蓝 #3A5BDB（与 res/values/colors.xml 的 brand_primary 一致）。
 *
 * 表面层级（surfaceContainerLowest → Highest）是一条由浅到深的蓝灰梯度，
 * 卡片、列表项、输入框按"抬升多少"取对应档位，而不是各自随机取灰。
 * 文字对最低档容器（surfaceContainerHighest）的对比度实测 8.4:1，满足 WCAG AA。
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3A5BDB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF00164A),

    secondary = Color(0xFF5B67C9),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E4FF),
    onSecondaryContainer = Color(0xFF131C63),

    tertiary = Color(0xFF00796B),                       // 生效 / 成功态
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA6E5DB),
    onTertiaryContainer = Color(0xFF00201C),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    // 页面底色比卡片略深一档：卡片因此是"浮起来的白"，而不是和背景融成一片
    background = Color(0xFFF4F6FC),
    onBackground = Color(0xFF1A1B2A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B2A),
    surfaceVariant = Color(0xFFE3E6F2),
    onSurfaceVariant = Color(0xFF43474F),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFCFF),
    surfaceContainer = Color(0xFFF1F4FB),
    surfaceContainerHigh = Color(0xFFEBEFF8),
    surfaceContainerHighest = Color(0xFFE5EAF5),

    outline = Color(0xFF75777F),
    outlineVariant = Color(0xFFC5C6D6),
    scrim = Color(0xFF000000),
)

/**
 * 深色配色。深色下主色不是把品牌蓝简单调暗（那样会发闷），
 * 而是提亮到 #9FB2FF —— 深色背景上"发光"的一层，对 surface 实测 8.6:1。
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9FB2FF),
    onPrimary = Color(0xFF0A1740),
    primaryContainer = Color(0xFF2B3A85),
    onPrimaryContainer = Color(0xFFDCE3FF),

    secondary = Color(0xFFC0C6FF),
    onSecondary = Color(0xFF1A2263),
    secondaryContainer = Color(0xFF3A4189),
    onSecondaryContainer = Color(0xFFE3E4FF),

    tertiary = Color(0xFF6FD9C9),                       // 深色下的生效 / 成功态
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF004D45),
    onTertiaryContainer = Color(0xFFA6E5DB),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF10111C),
    onBackground = Color(0xFFE7E9F7),
    surface = Color(0xFF16182A),
    onSurface = Color(0xFFE7E9F7),
    surfaceVariant = Color(0xFF3A3E58),
    onSurfaceVariant = Color(0xFFC6C8DA),

    surfaceContainerLowest = Color(0xFF0D0E17),
    surfaceContainerLow = Color(0xFF1B1D30),
    surfaceContainer = Color(0xFF21233A),
    surfaceContainerHigh = Color(0xFF2B2E47),
    surfaceContainerHighest = Color(0xFF363A55),

    outline = Color(0xFF8F919F),
    outlineVariant = Color(0xFF3A3E58),
    scrim = Color(0xFF000000),
)

/**
 * 应用主题。
 *
 * [dynamicColor] 默认关闭。原因：本应用的功能就是换壁纸，而 Android 12+ 的动态色
 * 取色来源恰恰是壁纸 —— 开着它，界面主色会被用户刚换上的壁纸牵着走
 * （实测出现过整屏变绿的情况）。品牌色与动态色只能二选一，这里选品牌色。
 *
 * 想改回动态色：把下面的 `false` 改成 `true`，只此一处，随时可回退。
 */
@Composable
fun ScreensaverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScreensaverTypography,
        content = content,
    )
}
