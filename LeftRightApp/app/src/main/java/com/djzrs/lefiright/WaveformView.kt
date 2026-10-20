package com.djzrs.lefiright

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.djzrs.lefiright.AudioProcessor.Segment
import kotlin.math.abs

/**
 * 波形视图：
 * - 显示音频波形与选区
 * - 分段色块 / 分割线 / 点击选中段 / 长按添加分割点
 * - 播放进度线（playhead）
 * - 双指缩放、单指平移、双击复位视图
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var waveform: FloatArray = FloatArray(0)
    var durationMs: Long = 0L
    var selectionStartMs: Long = 0L
    var selectionEndMs: Long = 0L

    /** 分段列表（由 MainActivity 维护） */
    var segments: List<Segment> = emptyList()
    var selectedSegmentIndex: Int = 0

    /** 播放进度（毫秒），null 表示不显示 */
    var playheadMs: Long? = null
        set(value) {
            field = value
            invalidate()
        }

    var onSelectionChanged: ((Long, Long) -> Unit)? = null
    var onSegmentSelected: ((Int) -> Unit)? = null
    var onSplitRequested: ((Long) -> Unit)? = null

    // 可视区域（缩放/平移）
    private var viewStartMs = 0L
    private var viewDurationMs = 0L

    private val handler = Handler(Looper.getMainLooper())

    private val waveformPaint = Paint().apply {
        color = 0xFF00E5FF.toInt()
        isAntiAlias = true
        strokeWidth = 2f
    }
    private val maskPaint = Paint().apply { color = 0x88000000.toInt() }
    private val handlePaint = Paint().apply {
        color = 0xFFFF3B30.toInt()
        isAntiAlias = true
        strokeWidth = 3f
    }
    private val handleFillPaint = Paint().apply {
        color = 0xFFFF3B30.toInt()
        isAntiAlias = true
    }
    private val centerLinePaint = Paint().apply {
        color = 0x33FFFFFF.toInt()
        strokeWidth = 1f
    }
    private val boundaryPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        isAntiAlias = true
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f)
    }
    private val selectionBorderPaint = Paint().apply {
        color = 0xFFFFFF00.toInt()
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val playheadPaint = Paint().apply {
        color = 0xFFFFD700.toInt()
        isAntiAlias = true
        strokeWidth = 5f
    }

    private val segmentColors = listOf(
        0x2200E5FF.toInt(), 0x22FF3B30.toInt(), 0x22FFD700.toInt(),
        0x337B68EE.toInt(), 0x2233FF99.toInt(), 0x22FF69B4.toInt()
    )
    private val segmentPaints = segmentColors.map { color ->
        Paint().apply { this.color = color }
    }

    private var dragging = NONE
    private var downX = 0f
    private var downY = 0f
    private var lastPanX = 0f
    private var panning = false
    private var multiTouch = false
    private var longPressTriggered = false
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private val touchSlop = 60f
    private val longPressTimeout = 500L

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        onSplitRequested?.invoke(timeForX(downX))
    }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (viewDurationMs <= 0 || durationMs <= 0) return false
                val focusRatio = (detector.focusX / width.toFloat()).coerceIn(0f, 1f)
                val centerMs = viewStartMs + (focusRatio * viewDurationMs).toLong()
                val newDuration = (viewDurationMs / detector.scaleFactor)
                    .toLong()
                    .coerceIn(MIN_VIEW_DURATION, durationMs)
                val maxStart = (durationMs - newDuration).coerceAtLeast(0L)
                val newStart = (centerMs - (focusRatio * newDuration).toLong())
                    .coerceIn(0L, maxStart)
                viewStartMs = newStart
                viewDurationMs = newDuration
                invalidate()
                return true
            }
        })

    companion object {
        private const val NONE = 0
        private const val LEFT = 1
        private const val RIGHT = 2
        private const val MIN_VIEW_DURATION = 2_000L
    }

    fun setData(waveform: FloatArray, durationMs: Long) {
        this.waveform = waveform
        this.durationMs = durationMs
        this.selectionStartMs = 0L
        this.selectionEndMs = durationMs
        this.segments = listOf(Segment(0L, durationMs, 0f))
        this.selectedSegmentIndex = 0
        this.playheadMs = null
        this.viewStartMs = 0L
        this.viewDurationMs = durationMs
        invalidate()
        onSelectionChanged?.invoke(selectionStartMs, selectionEndMs)
        onSegmentSelected?.invoke(0)
    }

    fun clearSegments() {
        segments = listOf(Segment(selectionStartMs, selectionEndMs, 0f))
        selectedSegmentIndex = 0
        invalidate()
        onSegmentSelected?.invoke(0)
    }

    /** 复位视图到全览 */
    fun resetView() {
        viewStartMs = 0L
        viewDurationMs = durationMs
        invalidate()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f

        canvas.drawLine(0f, centerY, w, centerY, centerLinePaint)

        // 波形（按可视区域映射）
        if (waveform.size > 0 && w > 0 && viewDurationMs > 0) {
            val maxBar = h * 0.42f
            val n = waveform.size
            for (i in 0 until n) {
                val t = (i.toFloat() / (n - 1f)) * durationMs
                val x = xForTime(t.toLong())
                if (x < -2f || x > w + 2f) continue
                val amp = waveform[i] * maxBar
                canvas.drawLine(x, centerY - amp, x, centerY + amp, waveformPaint)
            }
        }

        val startX = xForTime(selectionStartMs)
        val endX = xForTime(selectionEndMs)

        // 选区遮罩
        canvas.drawRect(0f, 0f, startX, h, maskPaint)
        canvas.drawRect(endX, 0f, w, h, maskPaint)

        // 分段颜色填充 + 分割线 + 选中边框
        if (segments.isNotEmpty()) {
            for ((i, seg) in segments.withIndex()) {
                val sx = xForTime(seg.startMs)
                val ex = xForTime(seg.endMs)
                canvas.drawRect(sx, 0f, ex, h, segmentPaints[i % segmentPaints.size])
            }
            for (i in 1 until segments.size) {
                val x = xForTime(segments[i].startMs)
                canvas.drawLine(x, 0f, x, h, boundaryPaint)
            }
            val selected = segments.getOrNull(selectedSegmentIndex)
            if (selected != null) {
                val sx = xForTime(selected.startMs)
                val ex = xForTime(selected.endMs)
                canvas.drawRect(sx, 0f, ex, h, selectionBorderPaint)
            }
        }

        // 播放进度线
        val playhead = playheadMs
        if (playhead != null) {
            val px = xForTime(playhead)
            if (px >= 0f && px <= w) {
                canvas.drawLine(px, 0f, px, h, playheadPaint)
            }
        }

        // 手柄
        drawHandle(canvas, startX, h)
        drawHandle(canvas, endX, h)
    }

    private fun drawHandle(canvas: Canvas, x: Float, h: Float) {
        canvas.drawLine(x, 0f, x, h, handlePaint)
        val radius = dp(12)
        canvas.drawCircle(x, h / 2f, radius, handleFillPaint)
        canvas.drawCircle(x, h / 2f, radius, handlePaint)
        canvas.drawLine(x - radius / 2f, h / 2f, x + radius / 2f, h / 2f, handlePaint)
    }

    private fun xForTime(ms: Long): Float {
        if (viewDurationMs <= 0) return 0f
        return ((ms - viewStartMs).toFloat() / viewDurationMs) * width.toFloat()
    }

    private fun timeForX(x: Float): Long {
        if (viewDurationMs <= 0) return 0
        val ratio = (x / width.toFloat()).coerceIn(0f, 1f)
        return (viewStartMs + ratio * viewDurationMs).toLong()
    }

    private fun segmentIndexAt(x: Float): Int {
        val t = timeForX(x)
        for ((i, seg) in segments.withIndex()) {
            if (t >= seg.startMs && t < seg.endMs) return i
        }
        return -1
    }

    private fun panBy(dxPx: Float) {
        if (durationMs <= 0 || viewDurationMs >= durationMs) return
        val deltaMs = (dxPx / width.toFloat() * viewDurationMs).toLong()
        val maxStart = (durationMs - viewDurationMs).coerceAtLeast(0L)
        viewStartMs = (viewStartMs - deltaMs).coerceIn(0L, maxStart)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0) return false
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                handler.removeCallbacks(longPressRunnable)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    multiTouch = false
                }
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastPanX = event.x
                panning = false
                multiTouch = false
                longPressTriggered = false
                lastTapTime = 0
                val startX = xForTime(selectionStartMs)
                val endX = xForTime(selectionEndMs)
                dragging = when {
                    abs(event.x - startX) < touchSlop -> LEFT
                    abs(event.x - endX) < touchSlop -> RIGHT
                    else -> NONE
                }
                if (dragging != NONE) {
                    return true
                }
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, longPressTimeout)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (multiTouch) {
                    return true
                }
                if (dragging == LEFT) {
                    val t = timeForX(event.x)
                    selectionStartMs = t.coerceIn(0L, selectionEndMs)
                    invalidate()
                    onSelectionChanged?.invoke(selectionStartMs, selectionEndMs)
                    return true
                } else if (dragging == RIGHT) {
                    val t = timeForX(event.x)
                    selectionEndMs = t.coerceIn(selectionStartMs, durationMs)
                    invalidate()
                    onSelectionChanged?.invoke(selectionStartMs, selectionEndMs)
                    return true
                } else {
                    if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                        handler.removeCallbacks(longPressRunnable)
                        if (viewDurationMs < durationMs) {
                            panning = true
                            panBy(event.x - lastPanX)
                            lastPanX = event.x
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (dragging == NONE && !longPressTriggered && !panning && !multiTouch) {
                    val index = segmentIndexAt(event.x)
                    if (index >= 0) {
                        selectedSegmentIndex = index
                        invalidate()
                        onSegmentSelected?.invoke(index)
                    }
                    // 双击复位
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 300 && abs(event.x - lastTapX) < dp(80)) {
                        resetView()
                        lastTapTime = 0
                    } else {
                        lastTapTime = now
                        lastTapX = event.x
                    }
                }
                dragging = NONE
                panning = false
                multiTouch = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dp(value: Int): Float = resources.displayMetrics.density * value
}