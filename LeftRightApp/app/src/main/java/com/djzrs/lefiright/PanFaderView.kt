package com.djzrs.lefiright

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 立体声推子。
 * 中间 = 立体声（左右各 100%），往左 = 左声道占比大，往右 = 右声道占比大。
 */
class PanFaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var pan: Float = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
            invalidate()
        }

    var onPanChanged: ((Float) -> Unit)? = null

    private val trackPaint = Paint().apply {
        color = 0xFF333344.toInt()
        isAntiAlias = true
    }
    private val trackActivePaint = Paint().apply {
        color = 0xFF00E5FF.toInt()
        isAntiAlias = true
    }
    private val centerPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        isAntiAlias = true
        strokeWidth = 3f
    }
    private val thumbPaint = Paint().apply {
        color = 0xFF00E5FF.toInt()
        isAntiAlias = true
    }
    private val thumbBorderPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val thumbLinePaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 2f
    }
    private val labelPaint = Paint().apply {
        color = 0xFFAAAAAA.toInt()
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private var dragging = false

    private val centerX: Float get() = width * 0.5f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h * 0.5f
        val trackWidth = w * 0.86f
        val left = (w - trackWidth) / 2f
        val right = left + trackWidth
        val thumbRadius = dp(22)

        // 轨道
        canvas.drawRoundRect(left, centerY - dp(5), right, centerY + dp(5), dp(5), dp(5), trackPaint)

        // 活动轨道（中间到推子位置）
        val thumbX = left + (pan + 1f) / 2f * trackWidth
        if (thumbX >= centerX) {
            canvas.drawRoundRect(centerX, centerY - dp(5), thumbX, centerY + dp(5), dp(5), dp(5), trackActivePaint)
        } else {
            canvas.drawRoundRect(thumbX, centerY - dp(5), centerX, centerY + dp(5), dp(5), dp(5), trackActivePaint)
        }

        // 中间刻度
        canvas.drawLine(centerX, centerY - dp(14), centerX, centerY + dp(14), centerPaint)

        // L / R 标签
        labelPaint.textSize = dp(14)
        canvas.drawText("L", left + dp(6), centerY + dp(34), labelPaint)
        canvas.drawText("R", right - dp(6), centerY + dp(34), labelPaint)

        // 推子钮
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbBorderPaint)
        canvas.drawLine(thumbX, centerY - dp(6), thumbX, centerY + dp(6), thumbLinePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val trackWidth = width.toFloat() * 0.86f
        val left = (width - trackWidth) / 2f
        val thumbX = left + (pan + 1f) / 2f * trackWidth
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragging = abs(event.x - thumbX) <= dp(44)
                if (dragging) {
                    updatePan(event.x)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    updatePan(event.x)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePan(x: Float) {
        val trackWidth = width.toFloat() * 0.86f
        val left = (width - trackWidth) / 2f
        val right = left + trackWidth
        val newPan = ((x - left) / (right - left) * 2f - 1f).coerceIn(-1f, 1f)
        pan = newPan
        onPanChanged?.invoke(pan)
    }

    private fun dp(value: Int): Float = resources.displayMetrics.density * value
}