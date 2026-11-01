package com.screensaver.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.screensaver.ScreensaverApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机、应用更新或精确闹钟权限恢复后重排闹钟。
 *
 * 设备重启后 AlarmManager 的闹钟会全部丢失，必须在此重新注册，
 * 否则定时切换会在重启后失效。
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val restoresAfterBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        val exactAlarmGranted = action == ACTION_EXACT_ALARM_PERMISSION_GRANTED
        if (!restoresAfterBoot && !exactAlarmGranted) return

        val app = context.applicationContext as? ScreensaverApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (exactAlarmGranted && !app.container.alarmScheduler.canScheduleExact()) {
                    return@launch
                }
                app.container.alarmScheduler.rescheduleNext()
                if (restoresAfterBoot) {
                    // 开机后立即应用当前应生效规则，避免重启后一直停在原壁纸
                    app.container.applyCurrentNow()
                }
                Log.d(TAG, "系统状态变化后已恢复切换闹钟：action=$action")
            } catch (e: Exception) {
                Log.e(TAG, "重排闹钟失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootCompleted"
        private const val ACTION_EXACT_ALARM_PERMISSION_GRANTED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
