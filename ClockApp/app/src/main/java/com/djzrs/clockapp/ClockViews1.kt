package com.djzrs.clockapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal fun weekCn(day: Int): String = when (day) {
    Calendar.SUNDAY -> "周日"
    Calendar.MONDAY -> "周一"
    Calendar.TUESDAY -> "周二"
    Calendar.WEDNESDAY -> "周三"
    Calendar.THURSDAY -> "周四"
    Calendar.FRIDAY -> "周五"
    Calendar.SATURDAY -> "周六"
    else -> ""
}

/** 1. 经典翻页时钟（Fliqlo 风格） */
class FlipClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val bgPaint = Paint().apply { color = 0xFF000000.toInt() }
    private val cardPaint = Paint().apply { isAntiAlias = true; color = 0xFF151515.toInt() }
    private val cardBorderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF3A3A3A.toInt()
    }
    private val linePaint = Paint().apply { color = 0xFF000000.toInt() }
    private val timePaint = Paint().apply {
        isAntiAlias = true
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val datePaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF9A9A9A.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        beginBurnIn(canvas)

        val c = Calendar.getInstance()
        val time = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val cardW = minOf(w * 0.92f, 980f)
        val cardH = minOf(h * 0.5f, 340f)
        val left = (w - cardW) / 2f
        val top = (h - cardH) / 2f - dp(40)
        val right = left + cardW
        val bottom = top + cardH
        val r = dp(16)
        canvas.drawRoundRect(left, top, right, bottom, r, r, cardPaint)
        canvas.drawRoundRect(left, top, right, bottom, r, r, cardBorderPaint)

        val midY = top + cardH / 2f
        canvas.drawLine(left + dp(8), midY, right - dp(8), midY, linePaint)

        timePaint.textSize = cardH * 0.58f
        val baseY = midY + timePaint.textSize * 0.36f

        canvas.save()
        canvas.clipRect(left, top, right, midY)
        canvas.drawText(time, w / 2f, baseY, timePaint)
        canvas.restore()

        canvas.save()
        canvas.clipRect(left, midY, right, bottom)
        canvas.drawText(time, w / 2f, baseY, timePaint)
        canvas.restore()

        datePaint.textSize = cardH * 0.09f
        val date = String.format(
            "%d年%02d月%02d日 %s",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH), weekCn(c.get(Calendar.DAY_OF_WEEK))
        )
        canvas.drawText(date, w / 2f, bottom + datePaint.textSize * 2.4f, datePaint)
        endBurnIn(canvas)
    }
}

/** 2. 极简数字时钟 */
class DigitalClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val bgPaint = Paint().apply { isAntiAlias = true }
    private val timePaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    }
    private val datePaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val secPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val colors = if (isDark) {
            intArrayOf(0xFF0D0D1A.toInt(), 0xFF1A1A2E.toInt(), 0xFF16213E.toInt())
        } else {
            intArrayOf(0xFFF5F7FA.toInt(), 0xFFE8EAF6.toInt(), 0xFFB0BEC5.toInt())
        }
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        beginBurnIn(canvas)

        val c = Calendar.getInstance()
        val time = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val date = String.format(
            "%d年%02d月%02d日 %s",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH), weekCn(c.get(Calendar.DAY_OF_WEEK))
        )

        timePaint.color = if (isDark) 0xFFFFFFFF.toInt() else 0xFF1A237E.toInt()
        datePaint.color = if (isDark) 0xFFB0BEC5.toInt() else 0xFF37474F.toInt()
        timePaint.textSize = minOf(w * 0.26f, h * 0.4f)
        datePaint.textSize = minOf(w * 0.055f, h * 0.075f)

        canvas.drawText(date, w / 2f, h * 0.34f, datePaint)
        canvas.drawText(time, w / 2f, h * 0.58f, timePaint)

        secPaint.textSize = timePaint.textSize * 0.16f
        secPaint.color = if (isDark) 0xFF78909C.toInt() else 0xFF78909C.toInt()
        val sec = String.format("%02d", c.get(Calendar.SECOND))
        canvas.drawText(sec, w / 2f + timePaint.textSize * 0.66f, h * 0.575f, secPaint)
        endBurnIn(canvas)
    }
}

/** 3. 模拟表盘时钟 */
class AnalogClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val bgPaint = Paint().apply { color = 0xFF000000.toInt() }
    private val facePaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF101018.toInt()
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF666666.toInt()
    }
    private val tickPaint = Paint().apply { isAntiAlias = true; color = 0xFF888888.toInt() }
    private val hourTickPaint = Paint().apply { isAntiAlias = true; color = 0xFFEEEEEE.toInt() }
    private val hourPaint = Paint().apply {
        isAntiAlias = true; color = 0xFFFFFFFF.toInt(); strokeWidth = 8f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val minutePaint = Paint().apply {
        isAntiAlias = true; color = 0xFFFFFFFF.toInt(); strokeWidth = 5f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val secondPaint = Paint().apply {
        isAntiAlias = true; color = 0xFFFF3B30.toInt(); strokeWidth = 2f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val centerPaint = Paint().apply { isAntiAlias = true; color = 0xFFFF3B30.toInt() }
    private val numPaint = Paint().apply {
        isAntiAlias = true; color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        beginBurnIn(canvas)

        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.36f
        canvas.drawCircle(cx, cy, radius, facePaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY) % 12 + c.get(Calendar.MINUTE) / 60f
        val minute = c.get(Calendar.MINUTE) + c.get(Calendar.SECOND) / 60f
        val second = c.get(Calendar.SECOND)

        for (i in 0 until 60) {
            val angle = Math.toRadians((i * 6 - 90).toDouble())
            val outer = radius - if (i % 5 == 0) dp(14) else dp(6)
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            val p = if (i % 5 == 0) hourTickPaint else tickPaint
            p.strokeWidth = if (i % 5 == 0) dp(3) else dp(1)
            canvas.drawLine(cx + outer * cosA, cy + outer * sinA, cx + radius * cosA, cy + radius * sinA, p)
        }

        numPaint.textSize = radius * 0.2f
        val textYOffset = numPaint.textSize * 0.35f
        canvas.drawText("12", cx, cy - radius * 0.72f + textYOffset, numPaint)
        canvas.drawText("3", cx + radius * 0.72f, cy + textYOffset, numPaint)
        canvas.drawText("6", cx, cy + radius * 0.72f + textYOffset, numPaint)
        canvas.drawText("9", cx - radius * 0.72f, cy + textYOffset, numPaint)

        val hourAngle = Math.toRadians((hour * 30 - 90).toDouble())
        val hourLen = radius * 0.5f
        canvas.drawLine(cx, cy, cx + cos(hourAngle).toFloat() * hourLen, cy + sin(hourAngle).toFloat() * hourLen, hourPaint)

        val minAngle = Math.toRadians((minute * 6 - 90).toDouble())
        val minLen = radius * 0.72f
        canvas.drawLine(cx, cy, cx + cos(minAngle).toFloat() * minLen, cy + sin(minAngle).toFloat() * minLen, minutePaint)

        val secAngle = Math.toRadians((second * 6 - 90).toDouble())
        val secLen = radius * 0.82f
        canvas.drawLine(cx, cy, cx + cos(secAngle).toFloat() * secLen, cy + sin(secAngle).toFloat() * secLen, secondPaint)

        canvas.drawCircle(cx, cy, dp(6), centerPaint)
        endBurnIn(canvas)
    }
}

/** 4. 霓虹灯时钟 */
class NeonClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val bgPaint = Paint().apply { color = 0xFF050510.toInt() }
    private val neonPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val datePaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        color = 0xFFAAAAAA.toInt()
    }
    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val colors = intArrayOf(
        0xFFFF2D78.toInt(), 0xFF00E5FF.toInt(),
        0xFFB388FF.toInt(), 0xFFFFB300.toInt()
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        beginBurnIn(canvas)

        val c = Calendar.getInstance()
        val time = String.format("%02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
        val neon = colors[(c.get(Calendar.SECOND) / 10) % colors.size]
        neonPaint.color = neon
        neonPaint.textSize = minOf(w * 0.24f, h * 0.4f)

        neonPaint.setShadowLayer(dp(24), 0f, 0f, neon)
        canvas.drawText(time, w / 2f, h * 0.55f, neonPaint)
        neonPaint.setShadowLayer(dp(48), 0f, 0f, neon)
        canvas.drawText(time, w / 2f, h * 0.55f, neonPaint)
        neonPaint.clearShadowLayer()

        linePaint.color = neon
        linePaint.setShadowLayer(dp(16), 0f, 0f, neon)
        canvas.drawLine(w * 0.14f, h * 0.28f, w * 0.86f, h * 0.28f, linePaint)
        canvas.drawLine(w * 0.14f, h * 0.74f, w * 0.86f, h * 0.74f, linePaint)
        linePaint.clearShadowLayer()

        datePaint.textSize = minOf(w * 0.05f, h * 0.07f)
        datePaint.setShadowLayer(dp(12), 0f, 0f, neon)
        val date = String.format("%d.%02d.%02d %s",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH), weekCn(c.get(Calendar.DAY_OF_WEEK)))
        canvas.drawText(date, w / 2f, h * 0.74f + datePaint.textSize * 2.2f, datePaint)
        datePaint.clearShadowLayer()
        endBurnIn(canvas)
    }
}