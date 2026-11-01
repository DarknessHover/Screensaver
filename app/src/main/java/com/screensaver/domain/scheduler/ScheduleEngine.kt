package com.screensaver.domain.scheduler

import com.screensaver.data.local.entities.ScheduleRuleEntity
import com.screensaver.data.local.entities.WallpaperDomain

/**
 * 调度引擎：纯函数，无 Android 运行时时依赖（操作的是纯 Kotlin 实体），便于单测。
 *
 * 时间模型：
 *  - 一天 = 0:00 → 24:00 的一条直线，被切成 0..1439 的"当天第几分钟"。
 *  - 一条规则永远不跨午夜（跨午夜由编辑器拆成两条存储），故 endMinute 永远 > startMinute。
 *  - 命中判断为 start <= now < end，无需 wrap-around 分支。
 *  - resolve 和冲突区间先按 [WallpaperDomain] 过滤，两域互不影响。
 */
object ScheduleEngine {

    /** 规则区间必须位于同一天内，且至少持续 1 分钟。 */
    fun isValidRange(startMinute: Int, endMinute: Int): Boolean =
        startMinute in 0 until MINUTES_PER_DAY &&
            endMinute in 1..MINUTES_PER_DAY &&
            startMinute < endMinute

    /**
     * 给定"当前分钟数(now ∈ [0,1439])"、全部规则与壁纸域，返回该域当前生效规则。
     *
     * @return 命中的规则；若无命中的规则则返回 null —— 调用方据此【不干预】，保持该域已有壁纸。
     */
    fun resolve(
        now: Int,
        rules: List<ScheduleRuleEntity>,
        domain: WallpaperDomain,
    ): ScheduleRuleEntity? {
        return rules.filter {
            it.wallpaperDomain == domain &&
                it.startMinute <= now && now < it.endMinute
        }.minByOrNull { it.startMinute }
    }

    /**
     * 收集真正会切换壁纸的开始分钟。
     * 空档结束时保持已有壁纸，不需要为 endMinute 单独唤醒。
     */
    fun startMinutes(rules: List<ScheduleRuleEntity>): Set<Int> =
        rules.asSequence()
            .filter { it.imageUri != null && isValidRange(it.startMinute, it.endMinute) }
            .map { it.startMinute }
            .toSet()

    /**
     * 返回指定域内所有规则的 (start, end) 区间对，给前端做"是否重叠"判断。
     * 排除传入的 ruleId（即"自己"），让编辑现有规则时不会与自己冲突。
     */
    fun getAllIntervals(
        rules: List<ScheduleRuleEntity>,
        domain: WallpaperDomain,
        excludeRuleId: Long? = null,
    ): List<Pair<Int, Int>> =
        rules
            .filter { it.wallpaperDomain == domain && it.id != excludeRuleId }
            .map { it.startMinute to it.endMinute }

    private const val MINUTES_PER_DAY = 1440
}
