package com.screensaver.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screensaver.data.local.entities.ScheduleRuleEntity
import com.screensaver.data.local.entities.WallpaperDomain
import com.screensaver.data.repository.ScreensaverRepository
import com.screensaver.di.AppContainer
import com.screensaver.domain.scheduler.ScheduleEngine
import com.screensaver.ui.util.formatMinute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 规则编辑状态的单一可信源。 */
data class RuleEditorState(
    val ruleId: Long? = null,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
    val selectedImageUri: String? = null,
    /** 本次编辑的规则所属壁纸域（LOCK=屏保 / SYSTEM=桌面）。 */
    val wallpaperDomain: WallpaperDomain = WallpaperDomain.SYSTEM,
    val isNew: Boolean = true,
    /** 其他规则占用的区间（start,end），用于禁选重叠。 */
    val occupiedIntervals: List<Pair<Int, Int>> = emptyList(),
    /** 保存提交中（防止重复点击产生多条相同规则的幂等保护）。 */
    val saving: Boolean = false,
)

class RuleEditorViewModel(private val container: AppContainer) : ViewModel() {

    private val repository: ScreensaverRepository = container.repository

    private val _state = MutableStateFlow(RuleEditorState())
    val state: StateFlow<RuleEditorState> = _state

    /** 一次性提示事件（冲突或失败原因），由界面弹 Snackbar。 */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events

    /**
     * 加载编辑状态。
     * @param defaultDomain 新建规则时的预选壁纸域（继承首页当前停留的 Tab）；
     *                      编辑已有规则时该参数被忽略，域以规则实体为准。
     */
    fun load(ruleId: Long?, defaultDomain: WallpaperDomain = WallpaperDomain.SYSTEM) {
        if (ruleId == null || ruleId < 0) {
            _state.value = RuleEditorState(isNew = true, wallpaperDomain = defaultDomain)
            viewModelScope.launch { loadOccupied(null) }
            return
        }
        viewModelScope.launch {
            val rule = repository.getRule(ruleId)
            _state.value = if (rule != null) {
                RuleEditorState(
                    ruleId = rule.id,
                    startMinute = rule.startMinute,
                    endMinute = rule.endMinute,
                    selectedImageUri = rule.imageUri,
                    wallpaperDomain = rule.wallpaperDomain,
                    isNew = false,
                )
            } else {
                RuleEditorState(isNew = true, wallpaperDomain = defaultDomain)
            }
            loadOccupied(ruleId)
        }
    }

    /** 加载【当前壁纸域内】其他规则的占用区间（排除自身，编辑时不与自己冲突）。 */
    private suspend fun loadOccupied(excludeRuleId: Long?) {
        val all = repository.getAllRules()
        val domain = _state.value.wallpaperDomain
        val intervals = ScheduleEngine.getAllIntervals(all, domain, excludeRuleId)
        _state.value = _state.value.copy(occupiedIntervals = intervals)
    }

    fun setRange(start: Int, end: Int) { _state.value = _state.value.copy(startMinute = start, endMinute = end) }

    /** 切换壁纸域后重新加载该域占用区间。 */
    fun setDomain(d: WallpaperDomain) {
        if (_state.value.wallpaperDomain == d) return
        _state.value = _state.value.copy(wallpaperDomain = d)
        viewModelScope.launch {
            val excludeId = if (_state.value.isNew) null else _state.value.ruleId
            loadOccupied(excludeId)
        }
    }

    /** 界面选图后写入素材 URI。 */
    fun setImageUri(uri: String?) {
        _state.value = _state.value.copy(selectedImageUri = uri)
    }

    /**
     * 保存前冲突校验：新(start,end)不得与任一已被占用的区间重叠。
     * @return 重叠区间文本（用于提示），无重叠则 null。
     */
    fun findConflict(rangeStart: Int, rangeEnd: Int): String? {
        return findConflictIn(rangeStart, rangeEnd, _state.value.occupiedIntervals)
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            // 幂等保护：保存中禁止再次提交，杜绝重复点击产生多条相同规则
            if (_state.value.saving) return@launch
            val s = _state.value

            if (s.selectedImageUri == null) {
                _events.tryEmit("请先为规则选择一张素材图片")
                return@launch
            }
            if (!ScheduleEngine.isValidRange(s.startMinute, s.endMinute)) {
                _events.tryEmit("时间段必须位于 00:00–24:00 内，且结束时间晚于开始时间")
                return@launch
            }

            _state.value = _state.value.copy(saving = true)
            try {
                val existing = if (!s.isNew) repository.getRule(s.ruleId!!) else null
                val currentIntervals = ScheduleEngine.getAllIntervals(
                    repository.getAllRules(),
                    s.wallpaperDomain,
                    excludeRuleId = s.ruleId,
                )
                val conflict = findConflictIn(
                    s.startMinute,
                    s.endMinute,
                    currentIntervals,
                )
                if (conflict != null) {
                    _events.tryEmit(conflict)
                    return@launch
                }
                val entity = ScheduleRuleEntity(
                    id = s.ruleId ?: 0,
                    startMinute = s.startMinute,
                    endMinute = s.endMinute,
                    imageUri = s.selectedImageUri,
                    wallpaperDomain = s.wallpaperDomain,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )
                if (s.isNew) repository.insertRule(entity)
                else repository.updateRule(entity)
                if (existing?.imageUri != s.selectedImageUri) {
                    container.releasePersistedUriIfUnused(existing?.imageUri)
                }
                container.alarmScheduler.rescheduleNext()
                container.applyCurrentNow()
                onSaved()
            } catch (e: Exception) {
                _events.tryEmit("保存失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                _state.value = _state.value.copy(saving = false)
            }
        }
    }
}

private fun findConflictIn(
    rangeStart: Int,
    rangeEnd: Int,
    intervals: List<Pair<Int, Int>>,
): String? = intervals
    .firstOrNull { (os, oe) -> overlaps(rangeStart, rangeEnd, os, oe) }
    ?.let { (os, oe) -> "与 ${formatMinute(os)}–${formatMinute(oe)} 的时间段冲突" }

/** 判断区间 [rs,re) 与已有区间 [os,oe) 是否重叠。 */
private fun overlaps(rs: Int, re: Int, os: Int, oe: Int): Boolean =
    rs < oe && os < re
