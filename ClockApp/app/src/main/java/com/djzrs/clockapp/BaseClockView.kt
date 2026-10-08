package com.djzrs.clockapp

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import java.util.Calendar

/**
 * 所有时钟样式的基类。
 * 负责每秒刷新、防烧屏偏移、日夜主题标记。
 */
abstract class BaseClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 是否为夜间主题（影响配色） */
    var isDark: Boolean = true

    /** 防烧屏位移 */
    var burnInOffsetX: Float = 0f
    var burnInOffsetY: Float = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            invalidate()
            // 对齐到下一秒，减少无谓刷新
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tickRunnable)
        super.onDetachedFromWindow()
    }

    protected fun now(): Calendar = Calendar.getInstance()

    protected fun dp(value: Int): Float = resources.displayMetrics.density * value
    protected fun dp(value: Float): Float = resources.displayMetrics.density * value

    /** 开始绘制时应用防烧屏偏移 */
    protected fun beginBurnIn(canvas: Canvas) {
        canvas.save()
        canvas.translate(burnInOffsetX, burnInOffsetY)
    }

    /** 结束绘制时恢复画布 */
    protected fun endBurnIn(canvas: Canvas) {
        canvas.restore()
    }
}