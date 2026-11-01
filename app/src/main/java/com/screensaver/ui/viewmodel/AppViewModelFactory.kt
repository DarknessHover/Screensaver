package com.screensaver.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.screensaver.di.AppContainer

/**
 * 统一 ViewModel 工厂：把整个 AppContainer 注入各 ViewModel，
 * 由 ViewModel 自行按需取用 repository / alarmScheduler / applyCurrentNow。
 */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(container) as T
            modelClass.isAssignableFrom(RuleEditorViewModel::class.java) ->
                RuleEditorViewModel(container) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: $modelClass")
        }
    }
}
