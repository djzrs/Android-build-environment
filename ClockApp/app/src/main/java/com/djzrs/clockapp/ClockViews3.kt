package com.djzrs.clockapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import java.util.Calendar
import kotlin.math.min

/** 9. 科幻 HUD 时钟 */
class HudClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val bgPaint = Paint().apply { color = 0xFF001510.toInt() }
    private val linePaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF00E5A0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val timePaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF00E5A0.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }
    private val smallPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF7AFFD6.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.NORMAL)
    }
    private val dimPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF4A9E8A.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.NORMAL)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        beginBurnIn(canvas)

        val c = Calendar.getInstance()
        val time = String.format("%02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
        val date = String.format("%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

        linePaint.setShadowLayer(dp(12), 0f, 0f, 0xFF00E5A0.toInt())

        // 四角装饰
        val corner = minOf(w, h) * 0.08f
        val m = dp(24)
        canvas.drawLine(m, m, m + corner, m, linePaint)
        canvas.drawLine(m, m, m, m + corner, linePaint)
        canvas.drawLine(w - m, m, w - m - corner, m, linePaint)
        canvas.drawLine(w - m, m, w - m, m + corner, linePaint)
        canvas.drawLine(m, h - m, m + corner, h - m, linePaint)
        canvas.drawLine(m, h - m, m, h - m - corner, linePaint)
        canvas.drawLine(w - m, h - m, w - m - corner, h - m, linePaint)
        canvas.drawLine(w - m, h - m, w - m, h - m - corner, linePaint)

        // 顶部标题
        smallPaint.textSize = minOf(w * 0.04f, h * 0.055f)
        canvas.drawText("SYS.TIME  //  UTC+8", w / 2f, h * 0.12f, smallPaint)

        // 中央时间
        timePaint.textSize = minOf(w * 0.22f, h * 0.34f)
        timePaint.setShadowLayer(dp(20), 0f, 0f, 0xFF00E5A0.toInt())
        canvas.drawText(time, w / 2f, h * 0.55f, timePaint)
        timePaint.clearShadowLayer()

        // 水平装饰线
        canvas.drawLine(w * 0.18f, h * 0.63f, w * 0.82f, h * 0.63f, linePaint)

        // 底部信息
        smallPaint.textSize = minOf(w * 0.045f, h * 0.06f)
        canvas.drawText("LOCAL DATE: $date", w / 2f, h * 0.75f, smallPaint)
        dimPaint.textSize = minOf(w * 0.04f, h * 0.05f)
        canvas.drawText("STATUS: RUNNING", w * 0.25f, h * 0.85f, dimPaint)
        canvas.drawText("SIGNAL: OK", w * 0.75f, h * 0.85f, dimPaint)

        linePaint.clearShadowLayer()
        endBurnIn(canvas)
    }
}

/** 10. 二进制时钟 */
class BinaryClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val bgPaint = Paint().apply { color = 0xFF000000.toInt() }
    private val onPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF00FF9C.toInt()
    }
    private val offPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF0A3A2A.toInt()
    }
    private val labelPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF00AA6E.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }
    private val valuePaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF66FFCC.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.NORMAL)
    }

    private fun bits(value: Int): IntArray {
        val arr = IntArray(4)
        var v = value
        for (i in 3 downTo 0) {
            arr[i] = v % 2
            v /= 2
        }
        return arr
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        beginBurnIn(canvas)

        val c = Calendar.getInstance()
        val hh = c.get(Calendar.HOUR_OF_DAY)
        val mm = c.get(Calendar.MINUTE)
        val ss = c.get(Calendar.SECOND)
        val values = intArrayOf(hh / 10, hh % 10, mm / 10, mm % 10, ss / 10, ss % 10)
        val titles = arrayOf("H1", "H2", "M1", "M2", "S1", "S2")

        onPaint.setShadowLayer(dp(10), 0f, 0f, 0xFF00FF9C.toInt())

        val r = minOf(w, h) * 0.045f
        val gapX = r * 3.2f
        val gapY = r * 2.8f
        val totalW = gapX * 5
        var cx = (w - totalW) / 2f + r
        val cy = h * 0.52f

        labelPaint.textSize = r * 2f
        for (i in 0 until 6) {
            val b = bits(values[i])
            labelPaint.color = 0xFF00AA6E.toInt()
            canvas.drawText(titles[i], cx, cy - gapY * 2.4f, labelPaint)
            for (row in 0 until 4) {
                val y = cy + (row - 1.5f) * gapY
                canvas.drawCircle(cx, y, r, if (b[3 - row] == 1) onPaint else offPaint)
            }
            valuePaint.textSize = r * 2.2f
            valuePaint.color = 0xFF66FFCC.toInt()
            canvas.drawText(values[i].toString(), cx, cy + gapY * 2.4f, valuePaint)
            cx += gapX
        }
        onPaint.clearShadowLayer()

        labelPaint.textSize = minOf(w * 0.04f, h * 0.055f)
        labelPaint.color = 0xFF339966.toInt()
        canvas.drawText("BINARY CLOCK", w / 2f, h * 0.12f, labelPaint)
        canvas.drawText(
            String.format("%02d:%02d:%02d", hh, mm, ss),
            w / 2f, h * 0.82f, valuePaint.apply { textSize = minOf(w * 0.07f, h * 0.09f); color = 0xFF00FF9C.toInt() }
        )
        endBurnIn(canvas)
    }
}

/** 11. 圆环时钟 */
class RingClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val bgPaint = Paint().apply { color = 0xFF000000.toInt() }
    private val ringBgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0xFF1A1A2A.toInt()
    }
    private val secRingPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0xFFFF3B30.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val minRingPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0xFF00E5FF.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val hourRingPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }
    private val labelPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF666666.toInt()
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
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        val second = c.get(Calendar.SECOND)
        val cx = w / 2f
        val cy = h / 2f
        val base = minOf(w, h) * 0.33f
        val stroke = base * 0.09f

        ringBgPaint.strokeWidth = stroke
        secRingPaint.strokeWidth = stroke
        minRingPaint.strokeWidth = stroke
        hourRingPaint.strokeWidth = stroke

        val secProgress = second / 60f * 360f
        val minProgress = (minute + second / 60f) / 60f * 360f
        val hourProgress = (hour % 12 + minute / 60f) / 12f * 360f

        canvas.drawCircle(cx, cy, base, ringBgPaint)
        canvas.drawCircle(cx, cy, base * 0.74f, ringBgPaint)
        canvas.drawCircle(cx, cy, base * 0.48f, ringBgPaint)

        secRingPaint.setShadowLayer(dp(8), 0f, 0f, 0xFFFF3B30.toInt())
        minRingPaint.setShadowLayer(dp(8), 0f, 0f, 0xFF00E5FF.toInt())
        hourRingPaint.setShadowLayer(dp(8), 0f, 0f, 0xFFFFFFFF.toInt())

        canvas.drawArc(RectF(cx - base, cy - base, cx + base, cy + base), -90f, secProgress, false, secRingPaint)
        canvas.drawArc(RectF(cx - base * 0.74f, cy - base * 0.74f, cx + base * 0.74f, cy + base * 0.74f), -90f, minProgress, false, minRingPaint)
        canvas.drawArc(RectF(cx - base * 0.48f, cy - base * 0.48f, cx + base * 0.48f, cy + base * 0.48f), -90f, hourProgress, false, hourRingPaint)

        textPaint.textSize = base * 0.3f
        canvas.drawText(String.format("%02d:%02d:%02d", hour, minute, second), cx, cy + textPaint.textSize * 0.35f, textPaint)

        labelPaint.textSize = base * 0.08f
        canvas.drawText("SEC", cx + base * 0.82f, cy - base * 0.7f, labelPaint)
        canvas.drawText("MIN", cx + base * 0.60f, cy - base * 0.86f, labelPaint)
        canvas.drawText("HOU", cx + base * 0.36f, cy - base * 0.98f, labelPaint)

        secRingPaint.clearShadowLayer()
        minRingPaint.clearShadowLayer()
        hourRingPaint.clearShadowLayer()
        endBurnIn(canvas)
    }
}

/** 12. 渐变时钟 */
class GradientClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val bgPaint = Paint().apply { isAntiAlias = true }
    private val timePaint = Paint().apply {
        isAntiAlias = true
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val datePaint = Paint().apply {
        isAntiAlias = true
        color = 0xCCFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private val palettes = listOf(
        intArrayOf(0xFF1A2980.toInt(), 0xFF26D0CE.toInt()),
        intArrayOf(0xFFFC466B.toInt(), 0xFF3F5EFB.toInt()),
        intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
        intArrayOf(0xFF42275A.toInt(), 0xFF734B6D.toInt()),
        intArrayOf(0xFFF953C6.toInt(), 0xFFB91D73.toInt()),
        intArrayOf(0xFF56AB2F.toInt(), 0xFFA8E063.toInt())
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        beginBurnIn(canvas)

        val c = Calendar.getInstance()
        val palette = palettes[(c.get(Calendar.SECOND) / 10) % palettes.size]
        bgPaint.shader = LinearGradient(0f, 0f, w, h, palette[0], palette[1], Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        timePaint.textSize = minOf(w * 0.26f, h * 0.4f)
        timePaint.setShadowLayer(dp(12), 0f, dp(4), 0x66000000.toInt())
        canvas.drawText(
            String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)),
            w / 2f, h * 0.55f, timePaint
        )
        timePaint.clearShadowLayer()

        datePaint.textSize = minOf(w * 0.055f, h * 0.075f)
        canvas.drawText(
            String.format("%d年%02d月%02d日 %s",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH), weekCn(c.get(Calendar.DAY_OF_WEEK))),
            w / 2f, h * 0.36f, datePaint
        )
        endBurnIn(canvas)
    }
}