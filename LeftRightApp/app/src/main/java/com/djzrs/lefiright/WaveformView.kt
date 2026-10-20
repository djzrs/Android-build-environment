package com.djzrs.lefiright

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 波形显示 + 剪辑选区控件。
 * 左右红色手柄可拖动，选区之外变暗。
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var waveform: FloatArray = FloatArray(0)
    var durationMs: Long = 0L
    var selectionStartMs: Long = 0L
    var selectionEndMs: Long = 0L

    var onSelectionChanged: ((Long, Long) -> Unit)? = null

    private val waveformPaint = Paint().apply {
        color = 0xFF00E5FF.toInt()
        isAntiAlias = true
        strokeWidth = 2f
    }
    private val centerLinePaint = Paint().apply {
        color = 0x33FFFFFF.toInt()
        strokeWidth = 1f
    }
    private val maskPaint = Paint().apply { color = 0x88000000.toInt() }
    private val handlePaint = Paint().apply {
        color = 0xFFFF3B30.toInt()
        isAntiAlias = true
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val handleFillPaint = Paint().apply {
        color = 0xFFFF3B30.toInt()
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private var dragging = NONE

    companion object {
        private const val NONE = 0
        private const val LEFT = 1
        private const val RIGHT = 2
    }

    fun setData(waveform: FloatArray, durationMs: Long) {
        this.waveform = waveform
        this.durationMs = durationMs
        this.selectionStartMs = 0L
        this.selectionEndMs = durationMs
        invalidate()
        onSelectionChanged?.invoke(selectionStartMs, selectionEndMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f

        canvas.drawLine(0f, centerY, w, centerY, centerLinePaint)

        if (waveform.size > 0 && w > 0) {
            val maxBar = h * 0.42f
            val n = waveform.size
            for (i in 0 until n) {
                val x = i / (n - 1f) * w
                val amp = waveform[i] * maxBar
                canvas.drawLine(x, centerY - amp, x, centerY + amp, waveformPaint)
            }
        }

        val startX = xForTime(selectionStartMs)
        val endX = xForTime(selectionEndMs)
        canvas.drawRect(0f, 0f, startX, h, maskPaint)
        canvas.drawRect(endX, 0f, w, h, maskPaint)

        drawHandle(canvas, startX, h)
        drawHandle(canvas, endX, h)
    }

    private fun drawHandle(canvas: Canvas, x: Float, h: Float) {
        canvas.drawLine(x, 0f, x, h, handlePaint)
        val radius = dp(12)
        canvas.drawCircle(x, h / 2f, radius, handleFillPaint)
        canvas.drawCircle(x, h / 2f, radius, handlePaint)
    }

    private fun xForTime(ms: Long): Float {
        if (durationMs <= 0) return 0f
        return (ms.toFloat() / durationMs) * width.toFloat()
    }

    private fun timeForX(x: Float): Long {
        if (durationMs <= 0) return 0L
        val ratio = (x / width.toFloat()).coerceIn(0f, 1f)
        return (ratio * durationMs).toLong()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0) return false
        val x = event.x
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val startX = xForTime(selectionStartMs)
                val endX = xForTime(selectionEndMs)
                dragging = when {
                    abs(x - startX) < dp(60) -> LEFT
                    abs(x - endX) < dp(60) -> RIGHT
                    else -> NONE
                }
                return dragging != NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging == LEFT) {
                    val t = timeForX(x)
                    selectionStartMs = t.coerceIn(0L, selectionEndMs)
                    invalidate()
                    onSelectionChanged?.invoke(selectionStartMs, selectionEndMs)
                    return true
                } else if (dragging == RIGHT) {
                    val t = timeForX(x)
                    selectionEndMs = t.coerceIn(selectionStartMs, durationMs)
                    invalidate()
                    onSelectionChanged?.invoke(selectionStartMs, selectionEndMs)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = NONE
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dp(value: Int): Float = resources.displayMetrics.density * value
}