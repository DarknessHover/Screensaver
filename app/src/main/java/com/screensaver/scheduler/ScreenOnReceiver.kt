package com.screensaver.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.screensaver.ScreensaverApplication
import com.screensaver.data.local.entities.WallpaperDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 亮屏补偿接收器（动态注册，见 ScreensaverApplication）。
 *
 * 灭屏/Doze 期间，系统壁纸服务可能暂停渲染锁屏图层。
 * 此时闹钟到点设置的锁屏壁纸（FLAG_LOCK）已写入系统缓存并返回成功，
 * 但锁屏界面不会立即重绘——表现为"息屏过点后锁屏仍是上一时间段壁纸，
 * 亮屏后锁屏重建才显示新壁纸"。
 *
 * 补偿：收到 SCREEN_ON（亮屏）后，对 LOCK 域重新应用当前生效规则，
 * 强制壁纸服务重新加载最新锁屏壁纸，提升"亮屏即见新屏保"的概率。
 * 仅处理 LOCK 域（桌面壁纸渲染即时，无此问题）。
 * 仍遵循静默运行约定：无常驻服务/轮询，只在亮屏瞬间做一次。
 */
class ScreenOnReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON) return
        val app = context.applicationContext as? ScreensaverApplication ?: return
        val container = app.container
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val applied = container.applyCurrentDomain(WallpaperDomain.LOCK)
                Log.d(TAG, "亮屏补偿完成：applied=$applied")
            } catch (e: Exception) {
                Log.e(TAG, "亮屏补偿异常", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScreenOn"
    }
}
