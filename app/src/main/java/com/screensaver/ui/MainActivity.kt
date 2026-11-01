package com.screensaver.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.screensaver.data.local.entities.WallpaperDomain
import com.screensaver.ui.screens.HomeScreen
import com.screensaver.ui.screens.RuleEditorScreen
import com.screensaver.ui.theme.ScreensaverTheme
import com.screensaver.ui.viewmodel.AppViewModelFactory
import com.screensaver.ui.viewmodel.HomeViewModel
import com.screensaver.ui.viewmodel.RuleEditorViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 每次打开应用：确保闹钟已排程，并立即应用当前应生效规则
        val container = appContainer()
        lifecycleScope.launch {
            container.alarmScheduler.rescheduleNext()
            container.applyCurrentNow()
        }
        setContent {
            ScreensaverTheme {
                val nav = rememberNavController()
                val factory = AppViewModelFactory(appContainer())

                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(nav, viewModel<HomeViewModel>(factory = factory))
                    }
                    composable(
                        route = "editor/{ruleId}?domain={domain}",
                        arguments = listOf(
                            navArgument("ruleId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument("domain") {
                                type = NavType.StringType
                                defaultValue = WallpaperDomain.SYSTEM.name
                            }
                        ),
                    ) { backStack ->
                        val id = backStack.arguments
                            ?.getLong("ruleId")
                            ?.takeIf { it >= 0L }
                        // 新建规则的预选域（继承首页当前 Tab）；编辑已有规则时忽略此参数
                        val domain = backStack.arguments
                            ?.getString("domain")
                            ?.let { d -> runCatching { WallpaperDomain.valueOf(d) }.getOrNull() }
                            ?: WallpaperDomain.SYSTEM
                        RuleEditorScreen(nav, id, domain, viewModel<RuleEditorViewModel>(factory = factory))
                    }
                }
            }
        }
    }
}
