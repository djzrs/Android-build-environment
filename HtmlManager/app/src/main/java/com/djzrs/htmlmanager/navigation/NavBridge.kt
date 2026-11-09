package com.djzrs.htmlmanager.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知栏、悬浮窗这类外部入口要跳页面时，先把目标塞进来，
 * 由 [AppNavHost] 在组合完成后取走——免得 Activity 拿着 navController 到处跑。
 */
object NavBridge {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun request(route: String) {
        _pending.value = route
    }

    fun consume() {
        _pending.value = null
    }
}
