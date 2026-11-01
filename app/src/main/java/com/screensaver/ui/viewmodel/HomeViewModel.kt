package com.screensaver.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screensaver.data.local.entities.ScheduleRuleEntity
import com.screensaver.data.local.entities.WallpaperDomain
import com.screensaver.di.AppContainer
import com.screensaver.domain.scheduler.ScheduleEngine
import com.screensaver.ui.util.nowMinuteOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页 ViewModel：展示所选域的规则与当前命中项，并处理权限、删除和立即应用。 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository

    /** 全部规则（来自 DB，未过滤未排序）。仅内部参与 [filteredRules] 的 combine，界面统一用 filteredRules。 */
    private val rules: StateFlow<List<ScheduleRuleEntity>> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 两域的当前生效规则各自独立。 */
    private val _currentLock = MutableStateFlow<ScheduleRuleEntity?>(null)

    private val _currentSystem = MutableStateFlow<ScheduleRuleEntity?>(null)

    /** 顶部域切换器当前所选项（默认屏保）。 */
    private val _selectedTab = MutableStateFlow(WallpaperDomain.LOCK)
    val selectedTab: StateFlow<WallpaperDomain> = _selectedTab

    /** 按所选域过滤 + 按 startMinute 升序排序后的规则列表（供 HomeScreen 直接渲染）。 */
    val filteredRules: StateFlow<List<ScheduleRuleEntity>> =
        combine(rules, _selectedTab) { all, tab ->
            all.asSequence()
                .filter { it.wallpaperDomain == tab }
                .sortedBy { it.startMinute }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前所选域的"当前生效规则"（与 Hero 卡联动）。 */
    val currentRuleForTab: StateFlow<ScheduleRuleEntity?> =
        combine(_currentLock, _currentSystem, _selectedTab) { lock, sys, tab ->
            when (tab) {
                WallpaperDomain.LOCK -> lock
                WallpaperDomain.SYSTEM -> sys
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 精确闹钟权限状态（API 31+）。未授权时 setExactAlarm 会降级为非精确模式。 */
    private val _exactAlarmGranted = MutableStateFlow(container.alarmScheduler.canScheduleExact())
    val exactAlarmGranted: StateFlow<Boolean> = _exactAlarmGranted

    /** 电池优化白名单状态（API 23+）。未加白时国产 ROM 可能冻结进程、后台到点不切换。 */
    private val _batteryWhitelisted = MutableStateFlow(true) // 初值 true：真值未复查前不误闪卡片
    val batteryWhitelisted: StateFlow<Boolean> = _batteryWhitelisted

    init {
        recomputeCurrent()
        refreshBatteryWhitelist()
    }

    /** 切换顶部域 Tab。 */
    fun selectTab(tab: WallpaperDomain) {
        if (_selectedTab.value != tab) _selectedTab.value = tab
    }

    /** 重新检测精确闹钟权限（从设置页返回时调用）。 */
    fun refreshExactAlarmStatus() {
        val granted = container.alarmScheduler.canScheduleExact()
        val newlyGranted = granted && !_exactAlarmGranted.value
        _exactAlarmGranted.value = granted
        if (newlyGranted) {
            viewModelScope.launch { container.alarmScheduler.rescheduleNext() }
        }
    }

    /**
     * 重新检测电池优化白名单（从系统设置返回时调用）。
     * 设置页不返回结果，因此回到前台后必须通过 PowerManager 读取真实状态。
     */
    fun refreshBatteryWhitelist() {
        _batteryWhitelisted.value = container.isIgnoringBatteryOptimizations()
    }

    fun recomputeCurrent() {
        viewModelScope.launch(Dispatchers.Default) {
            val now = nowMinuteOfDay()
            val all = repository.getAllRules()
            _currentLock.value = ScheduleEngine.resolve(now, all, WallpaperDomain.LOCK)
            _currentSystem.value = ScheduleEngine.resolve(now, all, WallpaperDomain.SYSTEM)
        }
    }

    fun deleteRule(rule: ScheduleRuleEntity) {
        viewModelScope.launch {
            repository.deleteRule(rule)
            container.releasePersistedUriIfUnused(rule.imageUri)
            container.alarmScheduler.rescheduleNext()
            recomputeCurrent()
        }
    }

    /** 手动立即应用当前应生效的规则（用于调试/即时预览）。 */
    fun applyNow() {
        viewModelScope.launch(Dispatchers.IO) { container.applyCurrentNow() }
    }
}
