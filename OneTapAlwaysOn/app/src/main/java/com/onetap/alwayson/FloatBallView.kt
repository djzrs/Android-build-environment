package com.onetap.alwayson

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
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
import kotlin.math.abs

class FloatBallView(
    context: Context,
    private val windowManager: WindowManager
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val ballSizePx = (63 * density).toInt()        // 63dp ≈ 10mm
    private val snapThresholdPx = (80 * density).toInt()   // 中心点距边缘小于该值才吸附
    private val unsnapOffsetPx = (24 * density).toInt()    // 变回球时向内挪的距离
    private val edgeMarginPx = (8 * density).toInt()

    private val screenWidth: Int
    private val screenHeight: Int

    val windowParams: WindowManager.LayoutParams

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var isDragging = false
    private var isSnapped = false

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

        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CC3F51B5"))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
                    isDragging = true
                    // 贴边态下向内滑动 → 脱离吸附
                    if (isSnapped && isInwardDrag(dx)) isSnapped = false
                }
                if (isDragging) {
                    windowParams.x = (dragStartX + dx).toInt()
                        .coerceIn(0, screenWidth - ballSizePx)
                    windowParams.y = (dragStartY + dy).toInt()
                        .coerceIn(0, screenHeight - ballSizePx)
                    windowManager.updateViewLayout(this, windowParams)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // 贴边态下的点击 → 变回球
                    if (isSnapped) unsnap()
                } else {
                    snapToEdgeIfNeeded()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                // 手势被系统打断：按拖动结束处理，避免状态残留
                if (isDragging) snapToEdgeIfNeeded()
                isDragging = false
            }
        }
        return true
    }

    private fun isInwardDrag(dx: Float): Boolean =
        if (dragStartX < screenWidth / 2) dx > 0 else dx < 0

    private fun snapToEdgeIfNeeded() {
        val centerX = windowParams.x + ballSizePx / 2f
        val targetX = when {
            centerX < snapThresholdPx -> 0
            screenWidth - centerX < snapThresholdPx -> screenWidth - ballSizePx
            else -> {
                isSnapped = false
                return
            }
        }
        animateX(targetX)
        isSnapped = true
    }

    private fun unsnap() {
        val inward = if (windowParams.x <= edgeMarginPx) 1 else -1
        val targetX = (windowParams.x + inward * unsnapOffsetPx)
            .coerceIn(0, screenWidth - ballSizePx)
        animateX(targetX)
        isSnapped = false
    }

    private fun animateX(targetX: Int) {
        ValueAnimator.ofInt(windowParams.x, targetX).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                windowParams.x = it.animatedValue as Int
                windowManager.updateViewLayout(this@FloatBallView, windowParams)
            }
            start()
        }
    }
}