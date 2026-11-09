package com.djzrs.htmlmanager.ui.server

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.djzrs.htmlmanager.server.ServerBridge
import com.djzrs.htmlmanager.server.ServerState
import com.djzrs.htmlmanager.server.ServerService
import kotlinx.coroutines.flow.StateFlow

/**
 * 服务器页只是观察者：真正的服务器跑在 [ServerService] 里，
 * 退出页面、退出 APP 都不影响，状态从 [ServerBridge] 读。
 */
class ServerViewModel(application: Application) : AndroidViewModel(application) {

    val state: StateFlow<ServerState> = ServerBridge.state

    fun start() {
        if (ServerBridge.state.value.running) return
        ServerService.start(getApplication())
    }

    fun stop() {
        ServerService.stop(getApplication())
    }

    fun consumeError() {
        ServerBridge.clearError()
    }
}
