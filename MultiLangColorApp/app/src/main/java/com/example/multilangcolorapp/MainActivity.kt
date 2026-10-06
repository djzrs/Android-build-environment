package com.example.multilangcolorapp

import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    
    private lateinit var rootLayout: View
    private lateinit var welcomeText: TextView
    private lateinit var infoText: TextView
    
    private var currentColor = Color.WHITE
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        rootLayout = findViewById(R.id.rootLayout)
        welcomeText = findViewById(R.id.welcomeText)
        infoText = findViewById(R.id.infoText)
        
        welcomeText.text = getString(R.string.welcome_message)
        
        // 点击切换颜色
        rootLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 生成随机颜色（不使用颜色列表）
                    currentColor = generateRandomColor()
                    rootLayout.setBackgroundColor(currentColor)
                    updateColorInfo()
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
        
        // 初始显示颜色信息
        rootLayout.post {
            updateColorInfo()
        }
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
        
        // 3秒后隐藏详细信息
        rootLayout.postDelayed({
            if (infoText.visibility == View.VISIBLE) {
                updateColorInfo()
            }
        }, 3000)
    }
}