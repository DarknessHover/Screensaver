package com.screensaver.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.screensaver.data.repository.ScreensaverRepository
import com.screensaver.domain.scheduler.ScheduleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 闹钟调度器：只注册距离当前最近的下一次规则开始闹钟。
 *
 * 设计要点：
 *  - 用 setExactAndAllowWhileIdle(RTC_WAKEUP) 保证在精确时间唤醒（省电模式下也能触发）。
 *  - 不在后台轮询；只在边界时刻唤醒一次并重算当前生效规则（见 WallpaperSwitchReceiver）。
 *  - 只保留一个 PendingIntent；触发后再计算下一次开始时间。
 *  - 若没有可调度规则：取消当前闹钟、不再注册，保持设备已有壁纸。
 *  - 所有规则默认生效，无 enabled 过滤。
 *
 * 自愈：闹钟触发后，WallpaperSwitchReceiver 会再次调用 rescheduleNext()，保证每天持续。
 */
class AlarmScheduler(
    private val context: Context,
    private val repository: ScreensaverRepository,
    private val engine: ScheduleEngine,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 重新规划下一次切换闹钟（开机 / 规则变更 / 权限恢复 / 自愈时调用）。 */
    suspend fun rescheduleNext() = withContext(Dispatchers.IO) {
        cancelCurrent()
        cancelLegacyAlarmsOnce()
        val rules = repository.getAllRules()
        if (rules.isEmpty()) return@withContext // 无规则时不注册新闹钟

        val now = Calendar.getInstance()
        val next = engine.startMinutes(rules)
            .map { minute -> minute to nextTriggerMillis(now, minute) }
            .minByOrNull { (_, triggerAt) -> triggerAt }
            ?: return@withContext

        alarmManager.setExactAlarm(next.second, pendingIntent())
        Log.d(TAG, "已排程下一次切换：minute=${next.first}, triggerAt=${next.second}")
    }

    /** 取消唯一的切换闹钟。 */
    private fun cancelCurrent() {
        existingPendingIntent()?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun nextTriggerMillis(now: Calendar, startMinute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startMinute / 60)
            set(Calendar.MINUTE, startMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now.timeInMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, WallpaperSwitchReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun existingPendingIntent(): PendingIntent? {
        val intent = Intent(context, WallpaperSwitchReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 升级自旧版多闹钟模型时，清理遗留的分钟请求码；仅执行一次且不创建新的 PendingIntent。 */
    private fun cancelLegacyAlarmsOnce() {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_LEGACY_ALARMS_CLEARED, false)) return

        val intent = Intent(context, WallpaperSwitchReceiver::class.java)
        for (requestCode in 1..MINUTES_PER_DAY) {
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
        preferences.edit().putBoolean(KEY_LEGACY_ALARMS_CLEARED, true).apply()
    }

    /**
     * 查询是否拥有 SCHEDULE_EXACT_ALARM 权限（API 31+）。
     * - 拥有：使用 setExactAndAllowWhileIdle（精确）
     * - 未拥有：降级到 setAndAllowWhileIdle（不精确，误差几分钟，但仍能在 Doze 下触发，不崩）
     */
    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            // API 30 及以下：无需特殊权限
            true
        }
    }

    private fun AlarmManager.setExactAlarm(triggerAtMillis: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+：必须 SCHEDULE_EXACT_ALARM 权限才能精确闹钟
            if (canScheduleExactAlarms()) {
                setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                // 降级：非精确模式，误差几分钟，仍能在 Doze 下唤醒（不崩）
                Log.w(TAG, "SCHEDULE_EXACT_ALARM 未授权，降级为 setAndAllowWhileIdle（误差几分钟）")
                setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } else {
            // minSdk 24：API 23 以下分支不可达。
            setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQUEST_CODE = 0
        private const val MINUTES_PER_DAY = 1440
        private const val PREFERENCES_NAME = "alarm_scheduler"
        private const val KEY_LEGACY_ALARMS_CLEARED = "legacy_alarms_cleared"
    }
}
