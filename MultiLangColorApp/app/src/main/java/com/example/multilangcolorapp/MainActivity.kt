package com.example.multilangcolorapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var welcomeText: TextView
    private lateinit var hintText: TextView
    private lateinit var infoText: TextView
    private lateinit var autoButton: Button
    private lateinit var historyContainer: LinearLayout

    private val history = ArrayDeque<Int>()
    private val maxHistory = 8

    private var currentColor = Color.WHITE
    private var autoChanging = false

    private val handler = Handler(Looper.getMainLooper())
    private val autoRunnable = object : Runnable {
        override fun run() {
            changeColor()
            handler.postDelayed(this, 2000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.rootLayout)
        welcomeText = findViewById(R.id.welcomeText)
        hintText = findViewById(R.id.hintText)
        infoText = findViewById(R.id.infoText)
        autoButton = findViewById(R.id.autoButton)
        historyContainer = findViewById(R.id.historyContainer)

        welcomeText.text = getString(R.string.welcome_message)
        hintText.text = getString(R.string.hint_text)

        // 点击切换颜色
        rootLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    changeColor()
                    true
                }
                else -> false
            }
        }

        // 长按显示详细信息
        rootLayout.setOnLongClickListener {
            showColorDetails()
            true
        }

        // 点击颜色值复制到剪贴板
        infoText.setOnClickListener {
            copyColorToClipboard()
        }

        // 自动变色开关
        autoButton.setOnClickListener {
            autoChanging = !autoChanging
            updateAutoButtonState()
            if (autoChanging) {
                handler.removeCallbacks(autoRunnable)
                handler.post(autoRunnable)
            } else {
                handler.removeCallbacks(autoRunnable)
            }
        }

        // 初始显示颜色信息
        rootLayout.post {
            updateColorInfo()
            updateAutoButtonState()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoRunnable)
        super.onDestroy()
    }

    private fun changeColor() {
        currentColor = generateRandomColor()
        rootLayout.setBackgroundColor(currentColor)
        addToHistory(currentColor)
        updateColorInfo()
        applyAdaptiveTextColor()
    }

    private fun generateRandomColor(): Int {
        val r = Random.nextInt(0, 256)
        val g = Random.nextInt(0, 256)
        val b = Random.nextInt(0, 256)
        return Color.rgb(r, g, b)
    }

    private fun updateColorInfo() {
        val colorHex = String.format("#%06X", 0xFFFFFF and currentColor)
        infoText.text = getString(R.string.current_color, colorHex)
        infoText.visibility = View.VISIBLE
    }

    private fun showColorDetails() {
        val r = Color.red(currentColor)
        val g = Color.green(currentColor)
        val b = Color.blue(currentColor)
        val colorHex = String.format("#%06X", 0xFFFFFF and currentColor)
        infoText.text = getString(R.string.color_detail, colorHex, r, g, b)
        infoText.visibility = View.VISIBLE

        // 3秒后恢复简略信息
        rootLayout.postDelayed({
            if (!autoChanging) {
                updateColorInfo()
            }
        }, 3000)
    }

    private fun copyColorToClipboard() {
        val colorHex = String.format("#%06X", 0xFFFFFF and currentColor)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("color", colorHex))
        Toast.makeText(this, getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
    }

    private fun addToHistory(color: Int) {
        if (history.contains(color)) {
            history.remove(color)
        }
        history.addFirst(color)
        while (history.size > maxHistory) {
            history.removeLast()
        }
        renderHistory()
    }

    private fun renderHistory() {
        historyContainer.removeAllViews()
        for (color in history) {
            val swatch = View(this)
            val size = dp(40)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(dp(4), 0, dp(4), 0)
            swatch.layoutParams = lp

            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE
            drawable.setColor(color)
            drawable.setStroke(dp(2), Color.WHITE)
            drawable.cornerRadius = dp(8).toFloat()
            swatch.background = drawable

            swatch.setOnClickListener {
                currentColor = color
                rootLayout.setBackgroundColor(currentColor)
                updateColorInfo()
                applyAdaptiveTextColor()
            }
            historyContainer.addView(swatch)
        }
    }

    private fun applyAdaptiveTextColor() {
        val luminance = (0.299 * Color.red(currentColor) +
                0.587 * Color.green(currentColor) +
                0.114 * Color.blue(currentColor)) / 255.0
        val textColor = if (luminance > 0.6) Color.BLACK else Color.WHITE
        welcomeText.setTextColor(textColor)
        hintText.setTextColor(
            Color.argb(
                180,
                Color.red(textColor),
                Color.green(textColor),
                Color.blue(textColor)
            )
        )
        infoText.setTextColor(textColor)
    }

    private fun updateAutoButtonState() {
        autoButton.text = if (autoChanging) {
            "${getString(R.string.auto_change)} ●"
        } else {
            getString(R.string.auto_change)
        }
        autoButton.alpha = if (autoChanging) 1.0f else 0.8f
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()
}