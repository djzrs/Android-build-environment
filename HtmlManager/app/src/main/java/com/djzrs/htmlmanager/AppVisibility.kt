package com.djzrs.htmlmanager

/**
 * APP 前后台状态的单一来源。`MainActivity` 在 `onStart` / `onStop` 里写，
 * 前台服务读它决定要不要挂悬浮窗——服务被系统重建后也能拿到正确的状态。
 */
object AppVisibility {

    @Volatile
    var isForeground: Boolean = true
        private set

    private var listener: ((Boolean) -> Unit)? = null

    fun setForeground(value: Boolean) {
        if (isForeground == value) return
        isForeground = value
        listener?.invoke(value)
    }

    fun observe(listener: (Boolean) -> Unit) {
        this.listener = listener
    }

    fun clearObserver() {
        listener = null
    }
}
