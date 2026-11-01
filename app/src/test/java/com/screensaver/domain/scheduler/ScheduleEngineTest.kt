package com.screensaver.domain.scheduler

import com.screensaver.data.local.entities.ScheduleRuleEntity
import com.screensaver.data.local.entities.WallpaperDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ScheduleEngine` 纯函数单测。
 *
 * 为什么这批测试重要：
 *  - 引擎是全应用"什么时候该显示哪张图"的唯一裁决者，改错一行 = 到点不换壁纸。
 *  - 它不依赖 Android 运行时，可在 JVM 上秒级跑完，无需装包、无需真机。
 *  - 后续任何"小步清理"（去重、重构、收敛分层）都能靠它快速确认行为未变。
 *
 * 覆盖的模型（对齐"剪开拉直"）：
 *  - 一天 = 0..1440 的一条直线，一条规则永远不跨午夜（endMinute > startMinute，且 ≤ 1440）。
 *  - 命中判断只有一种：`start <= now < end`（**左闭右开**）。
 */
class ScheduleEngineTest {

    // region 辅助构造

    private fun rule(
        id: Long = 0,
        start: Int,
        end: Int,
        domain: WallpaperDomain = WallpaperDomain.SYSTEM,
    ) = ScheduleRuleEntity(
        id = id,
        startMinute = start,
        endMinute = end,
        imageUri = "content://test/$id",
        wallpaperDomain = domain,
        createdAt = 0L,
    )

    /** 09:00 = 540，17:00 = 1020，24:00 = 1440 */
    private val at9to17 = rule(id = 1, start = 540, end = 1020)

    // endregion

    // region 基本命中

    @Test
    fun `命中区间中部`() {
        val rules = listOf(at9to17)
        // 12:00 = 720，落在 09:00–17:00 内
        assertEquals(1L, ScheduleEngine.resolve(720, rules, WallpaperDomain.SYSTEM)?.id)
    }

    @Test
    fun `左闭 —— 恰好等于 startMinute 时命中`() {
        val rules = listOf(at9to17)
        // 09:00 整点归属本条规则，而不是上一条
        assertEquals(1L, ScheduleEngine.resolve(540, rules, WallpaperDomain.SYSTEM)?.id)
    }

    @Test
    fun `右开 —— 恰好等于 endMinute 时不命中`() {
        val rules = listOf(at9to17)
        // 17:00 整点应交棒给下一条规则，本条不再命中
        assertNull(ScheduleEngine.resolve(1020, rules, WallpaperDomain.SYSTEM))
    }

    @Test
    fun `区间外不命中`() {
        val rules = listOf(at9to17)
        assertNull(ScheduleEngine.resolve(300, rules, WallpaperDomain.SYSTEM))  // 05:00 早于起点
        assertNull(ScheduleEngine.resolve(1200, rules, WallpaperDomain.SYSTEM)) // 20:00 晚于终点
    }

    @Test
    fun `无任何规则时返回 null（O4 不干预）`() {
        assertNull(ScheduleEngine.resolve(720, emptyList(), WallpaperDomain.SYSTEM))
    }

    // endregion

    // region 24:00 与跨午夜（"剪开拉直"模型）

    @Test
    fun `endMinute 为 1440 时，23点59分仍命中`() {
        val lateNight = rule(id = 2, start = 1320, end = 1440) // 22:00–24:00
        assertEquals(2L, ScheduleEngine.resolve(1439, listOf(lateNight), WallpaperDomain.SYSTEM)?.id)
    }

    @Test
    fun `now 为 1440 时不再命中（右开）`() {
        val lateNight = rule(id = 2, start = 1320, end = 1440)
        assertNull(ScheduleEngine.resolve(1440, listOf(lateNight), WallpaperDomain.SYSTEM))
    }

    @Test
    fun `跨午夜拆成两段后各自独立命中`() {
        // 用户要表达 22:00–02:00，需手动创建两条记录（这是"剪开拉直"模型的核心）
        val firstHalf = rule(id = 3, start = 1320, end = 1440) // 22:00–24:00
        val secondHalf = rule(id = 4, start = 0, end = 120)    // 00:00–02:00
        val rules = listOf(firstHalf, secondHalf)

        assertEquals(3L, ScheduleEngine.resolve(1380, rules, WallpaperDomain.SYSTEM)?.id) // 23:00
        assertEquals(4L, ScheduleEngine.resolve(60, rules, WallpaperDomain.SYSTEM)?.id)   // 01:00
        assertNull(ScheduleEngine.resolve(720, rules, WallpaperDomain.SYSTEM))            // 12:00 空档
    }

    // endregion

    // region 壁纸域隔离

    @Test
    fun `LOCK 域查询不会命中 SYSTEM 域规则`() {
        val rules = listOf(at9to17) // SYSTEM
        assertNull(ScheduleEngine.resolve(720, rules, WallpaperDomain.LOCK))
    }

    @Test
    fun `两域同名时间段各自独立生效`() {
        val lockRule = rule(id = 10, start = 540, end = 1020, domain = WallpaperDomain.LOCK)
        val sysRule = rule(id = 11, start = 540, end = 1020, domain = WallpaperDomain.SYSTEM)
        val rules = listOf(lockRule, sysRule)

        assertEquals(10L, ScheduleEngine.resolve(720, rules, WallpaperDomain.LOCK)?.id)
        assertEquals(11L, ScheduleEngine.resolve(720, rules, WallpaperDomain.SYSTEM)?.id)
    }

    // endregion

    // region 多条命中时的取舍

    @Test
    fun `多条重叠时取 startMinute 最小者`() {
        // 注：UI 层禁止重叠，此用例仅锁定引擎在异常数据下的确定性行为
        val later = rule(id = 20, start = 600, end = 900)  // 10:00–15:00
        val earlier = rule(id = 21, start = 540, end = 720) // 09:00–12:00
        val rules = listOf(later, earlier)

        assertEquals(21L, ScheduleEngine.resolve(700, rules, WallpaperDomain.SYSTEM)?.id)
    }

    // endregion

    // region startMinutes 与区间约束

    @Test
    fun `startMinutes 只收集有效且有图片的开始分钟并去重`() {
        val a = rule(id = 30, start = 540, end = 1020)  // 09:00–17:00
        val b = rule(id = 31, start = 540, end = 1440)  // 同时开始，另一域也只需一个闹钟
            .copy(wallpaperDomain = WallpaperDomain.LOCK)
        val noImage = rule(id = 32, start = 1200, end = 1300).copy(imageUri = null)
        val invalid = rule(id = 33, start = 1440, end = 1440)

        assertEquals(setOf(540), ScheduleEngine.startMinutes(listOf(a, b, noImage, invalid)))
    }

    @Test
    fun `startMinutes 无规则时为空`() {
        assertTrue(ScheduleEngine.startMinutes(emptyList()).isEmpty())
    }

    @Test
    fun `isValidRange 接受一天内左小于右的区间`() {
        assertTrue(ScheduleEngine.isValidRange(0, 1))
        assertTrue(ScheduleEngine.isValidRange(1320, 1440))
        assertTrue(!ScheduleEngine.isValidRange(1440, 1440))
        assertTrue(!ScheduleEngine.isValidRange(600, 600))
        assertTrue(!ScheduleEngine.isValidRange(-1, 60))
    }

    // endregion

    // region getAllIntervals（UI 禁重叠用）

    @Test
    fun `getAllIntervals 只返回同域区间`() {
        val lock = rule(id = 40, start = 0, end = 120, domain = WallpaperDomain.LOCK)
        val sys = rule(id = 41, start = 540, end = 1020, domain = WallpaperDomain.SYSTEM)

        assertEquals(
            listOf(0 to 120),
            ScheduleEngine.getAllIntervals(listOf(lock, sys), WallpaperDomain.LOCK),
        )
    }

    @Test
    fun `getAllIntervals 可排除自身（编辑时不与自己冲突）`() {
        val self = rule(id = 50, start = 540, end = 1020)
        val other = rule(id = 51, start = 1200, end = 1320)

        assertEquals(
            listOf(1200 to 1320),
            ScheduleEngine.getAllIntervals(listOf(self, other), WallpaperDomain.SYSTEM, excludeRuleId = 50),
        )
    }
}
