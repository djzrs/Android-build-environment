package com.onetap.alwayson

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import kotlin.math.abs
import kotlin.math.min

class FloatBallView(
    context: Context,
    private val windowManager: WindowManager
) : View(context) {

    companion object {
        const val PREFS_NAME = "settings"
        const val KEY_BALL_COLOR = "ball_color"
        const val KEY_SERVICE_RUNNING = "service_running"
        const val KEY_DURATION_MINUTES = "duration_minutes"
        const val KEY_WAKE_LOCK_HINT_SHOWN = "wake_lock_hint_shown"
    }

    private val density = context.resources.displayMetrics.density
    private val ballSizePx = (63 * density).toInt()        // 63dp ≈ 10mm
    private val barWidthPx = (28 * density).toInt()       // 贴边后的圆角竖条宽度
    private val barCornerRadiusPx = (barWidthPx / 2).toFloat()
    private val snapThresholdPx = (80 * density).toInt()   // 中心点距边缘小于该值才吸附
    private val unsnapOffsetPx = (24 * density).toInt()    // 变回球时向内挪的距离
    private val edgeMarginPx = (8 * density).toInt()

    private val screenWidth: Int
    private val screenHeight: Int

    val windowParams: WindowManager.LayoutParams

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var isDragging = false
    private var isSnapped = false
    private var currentAnimator: ValueAnimator? = null
    private var longPressTriggered = false
    private var lastClickTime = 0L

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        isDragging = false
        closeFloatBall()
    }

    @ColorInt
    private var ballColor: Int

    private val backgroundDrawable = GradientDrawable()

    private var countdownRemainingMillis = 0L
    private var countdownTotalMillis = 0L

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (3 * density)
        strokeCap = Paint.Cap.ROUND
        color = 0x66FFFFFF.toInt()
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (3 * density)
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    init {
        @Suppress("DEPRECATION")
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val size = Point()
            windowManager.defaultDisplay.getRealSize(size)
            Rect(0, 0, size.x, size.y)
        }
        screenWidth = bounds.width()
        screenHeight = bounds.height()

        windowParams = WindowManager.LayoutParams(
            ballSizePx,
            ballSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - ballSizePx - edgeMarginPx
            y = screenHeight / 3
        }

        ballColor = loadSavedColor()
        updateBackground(oval = true)
        background = backgroundDrawable
    }

    private fun loadSavedColor(): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rgb = prefs.getInt(KEY_BALL_COLOR, 0xFF3F51B5.toInt())
        return (0xCC shl 24) or (rgb and 0x00FFFFFF)
    }

    fun setColor(@ColorInt rgb: Int) {
        ballColor = (0xCC shl 24) or (rgb and 0x00FFFFFF)
        backgroundDrawable.setColor(ballColor)
    }

    fun updateCountdown(remainingMillis: Long, totalMillis: Long) {
        countdownRemainingMillis = remainingMillis
        countdownTotalMillis = totalMillis
        invalidate()
    }

    private fun updateBackground(oval: Boolean) {
        backgroundDrawable.shape =
            if (oval) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        if (!oval) {
            backgroundDrawable.cornerRadius = barCornerRadiusPx
        }
        backgroundDrawable.setColor(ballColor)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (countdownTotalMillis <= 0 || isSnapped || width <= 0) return
        val fraction = (countdownRemainingMillis.toFloat() / countdownTotalMillis)
            .coerceIn(0f, 1f)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - 4 * density
        val startAngle = -90f
        canvas.drawArc(
            centerX - radius, centerY - radius, centerX + radius, centerY + radius,
            startAngle, 360f, false, trackPaint
        )
        canvas.drawArc(
            centerX - radius, centerY - radius, centerX + radius, centerY + radius,
            startAngle, 360f * fraction, false, progressPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentAnimator?.cancel()
                longPressTriggered = false
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, longPressTimeout)
                downX = event.rawX
                downY = event.rawY
                dragStartX = windowParams.x
                dragStartY = windowParams.y
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    removeCallbacks(longPressRunnable)
                    isDragging = true
                    // 贴边态下向内滑动 → 立即脱离吸附，恢复圆形球
                    if (isSnapped && isInwardDrag(dx)) {
                        isSnapped = false
                        restoreBallInstantly()
                        downX = event.rawX
                        downY = event.rawY
                        dragStartX = windowParams.x
                        dragStartY = windowParams.y
                    }
                }
                if (isDragging) {
                    windowParams.x = (dragStartX + dx).toInt()
                        .coerceIn(0, screenWidth - windowParams.width)
                    windowParams.y = (dragStartY + dy).toInt()
                        .coerceIn(0, screenHeight - windowParams.height)
                    windowManager.updateViewLayout(this, windowParams)
                }
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (longPressTriggered) {
                    longPressTriggered = false
                    isDragging = false
                    return true
                }
                if (!isDragging) {
                    if (isSnapped) {
                        unsnap()
                    } else {
                        handleTap()
                    }
                } else {
                    snapToEdgeIfNeeded()
                }
                isDragging = false
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (isDragging) snapToEdgeIfNeeded()
                isDragging = false
            }
        }
        return true
    }

    private fun handleTap() {
        val now = System.currentTimeMillis()
        if (now - lastClickTime < doubleTapTimeout) {
            // 双击 → 关闭常亮
            lastClickTime = 0
            closeFloatBall()
        } else {
            lastClickTime = now
            openMainActivity()
        }
    }

    private fun restoreBallInstantly() {
        windowParams.x = if (windowParams.x <= 0) {
            unsnapOffsetPx
        } else {
            screenWidth - ballSizePx - unsnapOffsetPx
        }
        windowParams.width = ballSizePx
        windowParams.height = ballSizePx
        updateBackground(oval = true)
        windowManager.updateViewLayout(this, windowParams)
    }

    private fun isInwardDrag(dx: Float): Boolean =
        if (dragStartX < screenWidth / 2) dx > 0 else dx < 0

    private fun openMainActivity() {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        context.startActivity(intent)
    }

    private fun closeFloatBall() {
        val intent = Intent(context, FloatBallService::class.java)
            .setAction(FloatBallService.ACTION_HIDE)
        context.startService(intent)
    }

    private fun snapToEdgeIfNeeded() {
        val centerX = windowParams.x + windowParams.width / 2f
        val targetX: Int
        val targetWidth: Int
        when {
            centerX < snapThresholdPx -> {
                targetX = 0
                targetWidth = barWidthPx
            }
            screenWidth - centerX < snapThresholdPx -> {
                targetX = screenWidth - barWidthPx
                targetWidth = barWidthPx
            }
            else -> {
                isSnapped = false
                return
            }
        }
        animateTo(targetX, targetWidth, targetOval = false)
        isSnapped = true
    }

    private fun unsnap() {
        val targetX: Int
        if (windowParams.x <= 0) {
            targetX = unsnapOffsetPx
        } else {
            targetX = screenWidth - ballSizePx - unsnapOffsetPx
        }
        animateTo(targetX, ballSizePx, targetOval = true)
        isSnapped = false
    }

    private fun animateTo(targetX: Int, targetWidth: Int, targetOval: Boolean) {
        val startX = windowParams.x
        val startWidth = windowParams.width
        updateBackground(targetOval)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                windowParams.x = (startX + (targetX - startX) * f).toInt()
                windowParams.width = (startWidth + (targetWidth - startWidth) * f).toInt()
                windowManager.updateViewLayout(this@FloatBallView, windowParams)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                }
            })
            currentAnimator = this
            start()
        }
    }
}