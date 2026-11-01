package com.screensaver

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.screensaver.di.AppContainer
import com.screensaver.scheduler.ScreenOnReceiver

/**
 * 应用入口：构建并持有全局依赖容器（AppContainer）。
 *
 * 由于 BroadcastReceiver 由系统实例化（不走 Hilt/手动注入），
 * 这里把容器挂在 Application 上，接收器通过
 * `context.applicationContext as ScreensaverApplication` 取用。
 *
 * ACTION_SCREEN_ON 通过动态 Receiver 接收；该补偿只在应用进程存活期间生效。
 */
class ScreensaverApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        registerScreenOnReceiver()
    }

    /** 亮屏补偿：SCREEN_ON 时重设 LOCK 域当前壁纸，缓解"锁屏壁纸灭屏期间延迟生效"。 */
    private fun registerScreenOnReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ScreenOnReceiver(), filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(ScreenOnReceiver(), filter)
        }
    }
}
