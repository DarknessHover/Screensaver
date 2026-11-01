package com.screensaver.ui

import android.content.Context
import com.screensaver.ScreensaverApplication
import com.screensaver.di.AppContainer

/** 从任意 Context 取到全局依赖容器（接收器与 Compose 通用）。 */
fun Context.appContainer(): AppContainer =
    (applicationContext as ScreensaverApplication).container
