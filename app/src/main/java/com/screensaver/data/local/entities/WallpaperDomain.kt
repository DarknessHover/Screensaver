package com.screensaver.data.local.entities

/**
 * 壁纸域：
 *  - [LOCK]    屏保域 → 锁屏壁纸（FLAG_LOCK）
 *  - [SYSTEM]  桌面域 → 桌面壁纸（FLAG_SYSTEM，默认）
 * 两域各自拥有独立时间线和冲突检查，只应用到本域壁纸。
 */
enum class WallpaperDomain {
    LOCK,
    SYSTEM;

    companion object {
        /** 兼容机制：旧规则未声明域时回填为桌面域。 */
        fun fromStorage(value: String?): WallpaperDomain =
            entries.firstOrNull { it.name == value } ?: SYSTEM

        /** 面向 UI 的中文标签。 */
        fun labelOf(d: WallpaperDomain): String = when (d) {
            LOCK -> "屏保壁纸"
            SYSTEM -> "桌面壁纸"
        }

        /** 面向 UI 的竖屏/说明文字。 */
        fun hintOf(d: WallpaperDomain): String = when (d) {
            LOCK -> "设为本机的锁屏壁纸（部分机型锁屏由系统/主题接管）"
            SYSTEM -> "设为本机的桌面（主屏）壁纸"
        }
    }
}
