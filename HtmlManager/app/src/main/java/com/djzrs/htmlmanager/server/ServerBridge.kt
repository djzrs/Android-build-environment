package com.djzrs.htmlmanager.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 服务器对外状态。`error` 是一次性提示，展示后由 UI 调 [ServerBridge.clearError] 清掉。 */
data class ServerState(
    val running: Boolean = false,
    val address: String = "",
    val isLoopback: Boolean = false,
    val error: String? = null
)

/**
 * 服务器状态的唯一来源：`ServerService` 写入，服务器页与悬浮窗读取。
 * 服务器已经搬到前台服务里，UI 只是观察者，Activity 死活都不影响服务。
 */
object ServerBridge {

    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    fun publish(address: String, isLoopback: Boolean) {
        _state.update {
            it.copy(running = true, address = address, isLoopback = isLoopback, error = null)
        }
    }

    fun fail(message: String) {
        _state.update { it.copy(running = false, error = message) }
    }

    /** 服务停下：只清运行态，`error` 留着让 UI 消费——端口占用这类提示只在停止时才产生。 */
    fun reset() {
        _state.update { ServerState(error = it.error) }
    }

    fun clearError() {
        _state.update { if (it.error == null) it else it.copy(error = null) }
    }
}
