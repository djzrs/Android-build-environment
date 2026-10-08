package com.djzrs.clockapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import java.util.Calendar

/** 5. 七段数码管时钟（LED） */
class LedClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val bgPaint = Paint().apply { color = 0xFF000000.toInt() }
    private val segOnPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFFFF2A2A.toInt()
        style = Paint.Style.FILL
    }
    private val segOffPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF2A0000.toInt()
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint().apply { isAntiAlias = true; color = 0xFFFF2A2A.toInt() }

    // 数字 -> 七段(a,b,c,d,e,f,g) 是否点亮
    private val segments = mapOf(
        '0' to intArrayOf(1, 1, 1, 1, 1, 1, 0),
        '1' to intArrayOf(0, 1, 1, 0, 0, 0, 0),
        '2' to intArrayOf(1, 1, 0, 1, 1, 0, 1),
        '3' to intArrayOf(1, 1, 1, 1, 0, 0, 1),
        '4' to intArrayOf(0, 1, 1, 0, 0, 1, 1),
        '5' to intArrayOf(1, 0, 1, 1, 0, 1, 1),
        '6' to intArrayOf(1, 0, 1, 1, 1, 1, 1),
        '7' to intArrayOf(1, 1, 1, 0, 0, 0, 0),
        '8' to intArrayOf(1, 1, 1, 1, 1, 1, 1),
        '9' to intArrayOf(1, 1, 1, 1, 0, 1, 1)
    )

    private fun drawDigit(canvas: Canvas, digit: Char, x: Float, y: Float, l: Float, t: Float) {
        val seg = segments[digit] ?: intArrayOf(0, 0, 0, 0, 0, 0, 0)
        val paintOn = segOnPaint
        val paintOff = segOffPaint
        fun rect(left: Float, top: Float, right: Float, bottom: Float, on: Boolean) {
            canvas.drawRect(left, top, right, bottom, if (on) paintOn else paintOff)
        }
        rect(x - l, y - l - t / 2, x + l, y - l + t / 2, seg[0] == 1)   // a
        rect(x + l - t / 2, y - l, x + l + t / 2, y, seg[1] == 1)       // b
        rect(x + l - t / 2, y, x + l + t / 2, y + l, seg[2] == 1)       // c
        rect(x - l, y + l - t / 2, x + l, y + l + t / 2, seg[3] == 1)   // d
        rect(x - l - t / 2, y, x - l + t / 2, y + l, seg[4] == 1)       // e
        rect(x - l - t / 2, y - l, x - l + t / 2, y, seg[5] == 1)       // f
        rect(x - l, y - t / 2, x + l, y + t / 2, seg[6] == 1)           // g
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        beginBurnIn(canvas)

        val c = Calendar.getInstance()
        val text = String.format("%02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))

        segOnPaint.setShadowLayer(dp(18), 0f, 0f, 0xFFFF2A2A.toInt())
        dotPaint.setShadowLayer(dp(18), 0f, 0f, 0xFFFF2A2A.toInt())

        val l = minOf(w / 22f, h / 16f)
        val t = l * 0.2f
        val gap = l * 0.9f
        val digitW = l * 2 + t
        val totalW = digitW * 6 + gap * 5 + l * 2
        var x = (w - totalW) / 2f + l
        val y = h / 2f
        for (ch in text) {
            if (ch == ':') {
                val cx = x + l * 0.2f
                canvas.drawCircle(cx, y - l * 0.4f, t * 0.8f, dotPaint)
                canvas.drawCircle(cx, y + l * 0.4f, t * 0.8f, dotPaint)
                x += gap
            } else {
                drawDigit(canvas, ch, x, y, l, t)
                x += digitW + gap * 0.5f
            }
        }
        segOnPaint.clearShadowLayer()
        dotPaint.clearShadowLayer()

        val datePaint = Paint().apply {
            isAntiAlias = true
            color = 0xFF882222.toInt()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("monospace", Typeface.NORMAL)
        }
        datePaint.textSize = l * 0.6f
        val date = String.format("%04d-%02d-%02d %s",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH), weekCn(c.get(Calendar.DAY_OF_WEEK)))
        canvas.drawText(date, w / 2f, y + l * 2.4f, datePaint)
        endBurnIn(canvas)
    }
}

/** 6. 文字词钟（类似 QLOCKTWO） */
class WordClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val bgPaint = Paint().apply { color = 0xFF000000.toInt() }
    private val onPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val offPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF3A3A3A.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val hourWords = listOf(
        "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX",
        "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN", "TWELVE"
    )

    private fun activeWords(c: Calendar): Set<String> {
        var hour = c.get(Calendar.HOUR_OF_DAY) % 12
        val minute = c.get(Calendar.MINUTE)
        var m = minute
        var direction = "PAST"
        if (minute > 32) {
            hour = (hour + 1) % 12
            m = 60 - minute
            direction = "TO"
        }
        val words = mutableSetOf("IT", "IS")
        val rounded = ((m + 2) / 5) * 5 % 60
        when {
            rounded == 0 -> {
                words.add(hourWords[hour])
                words.add("OCLOCK")
            }
            else -> {
                when (rounded) {
                    5 -> words.add("FIVE")
                    10 -> words.add("TEN")
                    15 -> words.add("QUARTER")
                    20 -> { words.add("TWENTY"); words.add("FIVE") }
                    25 -> { words.add("TWENTY"); words.add("FIVE") }
                    30 -> words.add("HALF")
                }
                if (rounded == 5 || rounded == 10 || rounded == 20 || rounded == 25) {
                    words.add("MINUTES")
                }
                words.add(direction)
                words.add(hourWords[hour])
            }
        }
        return words
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        beginBurnIn(canvas)

        val c = Calendar.getInstance()
        val active = activeWords(c)

        val rows = listOf(
            listOf("IT", "IS"),
            listOf("HALF", "QUARTER", "TWENTY", "FIVE", "TEN"),
            listOf("MINUTES", "TO", "PAST"),
            listOf("ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX"),
            listOf("SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN", "TWELVE"),
            listOf("OCLOCK")
        )

        val maxRows = rows.size.toFloat()
        val cellH = h * 0.13f
        val textSize = cellH * 0.5f
        onPaint.textSize = textSize
        offPaint.textSize = textSize
        onPaint.setShadowLayer(dp(10), 0f, 0f, 0xFF00E5FF.toInt())

        var y = (h - cellH * maxRows) / 2f + textSize * 0.35f
        for (row in rows) {
            val cellW = w / (row.size + 1f)
            var x = cellW / 2f
            for (word in row) {
                val isOn = active.contains(word)
                canvas.drawText(word, x, y, if (isOn) onPaint else offPaint)
                x += cellW
            }
            y += cellH
        }
        onPaint.clearShadowLayer()

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = 0xFF666666.toInt()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        titlePaint.textSize = h * 0.045f
        canvas.drawText("WORD CLOCK", w / 2f, h * 0.06f, titlePaint)
        endBurnIn(canvas)
    }
}

/** 7. 计算器 LCD 时钟 */
class LcdClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val panelPaint = Paint().apply { isAntiAlias = true }
    private val digitPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }
    private val datePaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.NORMAL)
    }
    private val framePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0xFF3E2723.toInt()
        strokeWidth = 5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, Paint().apply { color = if (isDark) 0xFF1A120B.toInt() else 0xFF5D4037.toInt() })
        beginBurnIn(canvas)

        val c = Calendar.getInstance()
        val panelW = minOf(w * 0.9f, 800f)
        val panelH = minOf(h * 0.72f, 480f)
        val left = (w - panelW) / 2f
        val top = (h - panelH) / 2f
        val right = left + panelW
        val bottom = top + panelH

        val panelColor = if (isDark) 0xFF0E0E0E.toInt() else 0xFFD9D9C0.toInt()
        panelPaint.color = panelColor
        canvas.drawRoundRect(left, top, right, bottom, dp(16), dp(16), panelPaint)
        canvas.drawRoundRect(left, top, right, bottom, dp(16), dp(16), framePaint)

        val barPaint = Paint().apply {
            isAntiAlias = true
            color = if (isDark) 0xFF222222.toInt() else 0xFFB0A88A.toInt()
        }
        canvas.drawRoundRect(left + dp(30), top + dp(24), right - dp(30), top + dp(44), dp(8), dp(8), barPaint)

        val labelPaint = Paint().apply {
            isAntiAlias = true
            color = if (isDark) 0xFF66FF66.toInt() else 0xFF556B2F.toInt()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("monospace", Typeface.NORMAL)
        }
        labelPaint.textSize = panelH * 0.05f
        canvas.drawText("LCD DESK CLOCK", w / 2f, top + panelH * 0.13f, labelPaint)

        val time = String.format("%02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
        digitPaint.color = if (isDark) 0xFF33FF66.toInt() else 0xFF2E3B1F.toInt()
        digitPaint.textSize = panelH * 0.3f
        canvas.drawText(time, w / 2f, top + panelH * 0.5f, digitPaint)

        val date = String.format("%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        datePaint.color = if (isDark) 0xFF55AA55.toInt() else 0xFF556B2F.toInt()
        datePaint.textSize = panelH * 0.08f
        canvas.drawText(date, w / 2f, top + panelH * 0.7f, datePaint)

        endBurnIn(canvas)
    }
}

/** 8. 8-bit 像素时钟 */
class PixelClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseClockView(context, attrs) {

    private val bgPaint = Paint().apply { color = 0xFF0B0B1A.toInt() }
    private val pixelOnPaint = Paint().apply { isAntiAlias = true; color = 0xFF33FF99.toInt() }
    private val pixelOffPaint = Paint().apply { color = 0xFF12332A.toInt() }
    private val labelPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF33FF99.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }

    // 3x5 数字矩阵
    private val digits = arrayOf(
        intArrayOf(1, 1, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 1, 1), // 0
        intArrayOf(0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 1, 0, 1, 1, 1), // 1
        intArrayOf(1, 1, 1, 0, 0, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1), // 2
        intArrayOf(1, 1, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1), // 3
        intArrayOf(1, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0, 1, 0, 0, 1), // 4
        intArrayOf(1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 0, 1, 1, 1, 1), // 5
        intArrayOf(1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 0, 1, 1, 1, 1), // 6
        intArrayOf(1, 1, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 0), // 7
        intArrayOf(1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1), // 8
        intArrayOf(1, 1, 1, 1, 0, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1)  // 9
    )

    private fun drawPixelDigit(canvas: Canvas, digit: Int, x: Float, y: Float, cell: Float) {
        val matrix = digits[digit]
        for (row in 0 until 5) {
            for (col in 0 until 3) {
                val on = matrix[row * 3 + col] == 1
                canvas.drawRect(
                    x + col * cell, y + row * cell,
                    x + (col + 1) * cell, y + (row + 1) * cell,
                    if (on) pixelOnPaint else pixelOffPaint
                )
            }
        }
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
        val nums = intArrayOf(hh / 10, hh % 10, mm / 10, mm % 10, ss / 10, ss % 10)

        pixelOnPaint.setShadowLayer(dp(12), 0f, 0f, 0xFF33FF99.toInt())

        val cell = minOf(w / 30f, h / 20f)
        val gap = cell * 0.6f
        val digitW = cell * 3
        val colonW = cell * 2
        val totalW = digitW * 6 + gap * 5 + colonW * 2
        var x = (w - totalW) / 2f
        val y = h * 0.42f
        var idx = 0
        for (i in 0 until 6) {
            if (i == 2 || i == 4) {
                val cx = x + colonW / 2f
                canvas.drawRect(cx - cell * 0.2f, y + cell * 0.8f, cx + cell * 0.2f, y + cell * 1.2f, pixelOnPaint)
                canvas.drawRect(cx - cell * 0.2f, y + cell * 2.8f, cx + cell * 0.2f, y + cell * 3.2f, pixelOnPaint)
                x += colonW
            }
            drawPixelDigit(canvas, nums[idx], x, y, cell)
            x += digitW
            if (i == 0 || i == 1 || i == 3 || i == 4) x += gap
            idx++
        }
        pixelOnPaint.clearShadowLayer()

        labelPaint.textSize = cell * 2f
        canvas.drawText("8-BIT CLOCK", w / 2f, h * 0.2f, labelPaint)
        labelPaint.color = 0xFF33AA77.toInt()
        labelPaint.textSize = cell * 1.2f
        canvas.drawText(String.format("SCORE: %02d:%02d:%02d", hh, mm, ss), w / 2f, h * 0.28f, labelPaint)

        for (i in 0 until 12) {
            val on = ((c.get(Calendar.SECOND) + i) % 3 == 0)
            canvas.drawRect(
                w * 0.1f + i * cell * 0.8f, h * 0.85f,
                w * 0.1f + i * cell * 0.8f + cell * 0.5f, h * 0.85f + cell * 0.5f,
                if (on) pixelOnPaint else pixelOffPaint
            )
        }
        endBurnIn(canvas)
    }
}