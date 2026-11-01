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
 * 闹钟到点广播接收器。
 *
 * 收到后：
 *  1. 调用 [com.screensaver.di.AppContainer.applyCurrentNow] —— "按域解析当前生效规则并应用"的**唯一实现**
 *     （屏保域→锁屏壁纸 FLAG_LOCK、桌面域→桌面壁纸 FLAG_SYSTEM；某域未命中则不干预）。
 *  2. 自愈式重新排程，保证每天边界闹钟持续。
 *
 * 用 goAsync() 把耗时 IO（读图/设壁纸）放到 IO 协程，避免 ANR/超时。
 */
class WallpaperSwitchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ScreensaverApplication ?: return
        val container = app.container
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // 分域应用当前生效规则（决策与日志见 AppContainer.applyDomain）
                container.applyCurrentNow()

                // 自愈：重新排程（每日持续）
                container.alarmScheduler.rescheduleNext()
            } catch (e: Exception) {
                // Receiver 无法向界面返回错误，保留日志用于定位后台失败。
                Log.e(TAG, "切换处理异常", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "WallpaperSwitch"
    }
}
