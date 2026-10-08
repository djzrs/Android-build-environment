package com.djzrs.clockapp

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var clockContainer: FrameLayout
    private var currentStyle = 0
    private var burnInEnabled = true
    private var autoBrightnessEnabled = false

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null

    private val handler = Handler(Looper.getMainLooper())
    private val burnInRunnable = object : Runnable {
        override fun run() {
            shiftClockForBurnIn()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
        currentStyle = prefs.getInt("style", 0)
        burnInEnabled = prefs.getBoolean("burn_in", true)
        autoBrightnessEnabled = prefs.getBoolean("auto_brightness", false)

        applySavedTheme()
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        clockContainer = findViewById(R.id.clockContainer)
        clockContainer.setOnClickListener { showSettings() }
        applyStyle(currentStyle)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        handler.post(burnInRunnable)
        applyDirection(prefs.getInt("direction", 0))
    }

    override fun onResume() {
        super.onResume()
        if (autoBrightnessEnabled) {
            registerLightSensor()
        }
    }

    override fun onPause() {
        unregisterLightSensor()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(burnInRunnable)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemBars()
        updateClockTheme()
    }

    // ---------- 界面 ----------

    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    private fun applySavedTheme() {
        when (prefs.getInt("theme", 0)) {
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun applyDirection(direction: Int) {
        requestedOrientation = when (direction) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun updateClockTheme() {
        val view = clockContainer.getChildAt(0) as? BaseClockView
        view?.isDark = isDarkMode()
        view?.invalidate()
    }

    private fun applyStyle(style: Int) {
        clockContainer.removeAllViews()
        val view: View = when (style) {
            0 -> FlipClockView(this)
            1 -> DigitalClockView(this)
            2 -> AnalogClockView(this)
            3 -> NeonClockView(this)
            4 -> LedClockView(this)
            5 -> WordClockView(this)
            6 -> LcdClockView(this)
            7 -> PixelClockView(this)
            8 -> HudClockView(this)
            9 -> BinaryClockView(this)
            10 -> RingClockView(this)
            else -> GradientClockView(this)
        }
        if (view is BaseClockView) {
            view.isDark = isDarkMode()
            view.burnInOffsetX = prefs.getFloat("offset_x", 0f)
            view.burnInOffsetY = prefs.getFloat("offset_y", 0f)
        }
        clockContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    // ---------- 防烧屏 ----------

    private fun shiftClockForBurnIn() {
        if (!burnInEnabled) return
        val view = clockContainer.getChildAt(0) as? BaseClockView ?: return
        val dx = (Math.random() * 16 - 8).toInt().toFloat()
        val dy = (Math.random() * 16 - 8).toInt().toFloat()
        val max = dpFloat(40)
        val newX = (view.burnInOffsetX + dx).coerceIn(-max, max)
        val newY = (view.burnInOffsetY + dy).coerceIn(-max, max)
        view.burnInOffsetX = newX
        view.burnInOffsetY = newY
        prefs.edit().putFloat("offset_x", newX).putFloat("offset_y", newY).apply()
        view.invalidate()
    }

    // ---------- 环境光亮度 ----------

    private fun registerLightSensor() {
        lightSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun unregisterLightSensor() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LIGHT && autoBrightnessEnabled) {
            adjustScreenBrightness(event.values[0])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不需要处理
    }

    private fun adjustScreenBrightness(lux: Float) {
        val brightness = (lux / 1000f).coerceIn(0.08f, 1f)
        val lp = window.attributes
        lp.screenBrightness = brightness
        window.attributes = lp
    }

    private fun restoreSystemBrightness() {
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    // ---------- 设置面板 ----------

    private fun showSettings() {
        val dialog = BottomSheetDialog(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(dp(24), dp(16), dp(24), dp(32))

        layout.addView(createLabel(getString(R.string.settings_title), 18f, bold = true))

        layout.addView(createLabel(getString(R.string.style_label), 14f, bold = false))
        val styleNames = resources.getStringArray(R.array.clock_style_names)
        val grid = GridLayout(this)
        grid.columnCount = 3
        for (i in styleNames.indices) {
            val btn = Button(this)
            btn.text = styleNames[i]
            btn.isAllCaps = false
            btn.textSize = 12f
            if (i == currentStyle) {
                btn.setBackgroundColor(0xFF00E5FF.toInt())
                btn.setTextColor(0xFF000000.toInt())
            }
            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.height = dp(64)
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            btn.layoutParams = lp
            btn.setOnClickListener {
                currentStyle = i
                prefs.edit().putInt("style", i).apply()
                applyStyle(i)
                dialog.dismiss()
            }
            grid.addView(btn)
        }
        layout.addView(grid)

        layout.addView(createLabel(getString(R.string.direction_label), 14f, bold = false))
        val directionGroup = RadioGroup(this)
        directionGroup.orientation = RadioGroup.HORIZONTAL
        val directionPref = prefs.getInt("direction", 0)
        val directionLabels = arrayOf(
            getString(R.string.direction_auto),
            getString(R.string.direction_landscape),
            getString(R.string.direction_portrait)
        )
        for (i in 0..2) {
            val rb = RadioButton(this)
            rb.text = directionLabels[i]
            rb.id = i
            if (i == directionPref) rb.isChecked = true
            directionGroup.addView(rb)
        }
        directionGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putInt("direction", checkedId).apply()
            applyDirection(checkedId)
        }
        layout.addView(directionGroup)

        layout.addView(createLabel(getString(R.string.theme_label), 14f, bold = false))
        val themeGroup = RadioGroup(this)
        themeGroup.orientation = RadioGroup.HORIZONTAL
        val themePref = prefs.getInt("theme", 0)
        val themeLabels = arrayOf(
            getString(R.string.theme_follow),
            getString(R.string.theme_day),
            getString(R.string.theme_night)
        )
        for (i in 0..2) {
            val rb = RadioButton(this)
            rb.text = themeLabels[i]
            rb.id = i
            if (i == themePref) rb.isChecked = true
            themeGroup.addView(rb)
        }
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putInt("theme", checkedId).apply()
            when (checkedId) {
                1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            updateClockTheme()
        }
        layout.addView(themeGroup)

        val burnSwitch = SwitchMaterial(this)
        burnSwitch.text = getString(R.string.burn_in_label)
        burnSwitch.isChecked = burnInEnabled
        burnSwitch.setOnCheckedChangeListener { _, checked ->
            burnInEnabled = checked
            prefs.edit().putBoolean("burn_in", checked).apply()
        }
        layout.addView(burnSwitch)
        layout.addView(createHint(getString(R.string.burn_in_hint)))

        val autoSwitch = SwitchMaterial(this)
        autoSwitch.text = getString(R.string.auto_brightness_label)
        autoSwitch.isChecked = autoBrightnessEnabled
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            autoBrightnessEnabled = checked
            prefs.edit().putBoolean("auto_brightness", checked).apply()
            if (checked) {
                registerLightSensor()
            } else {
                unregisterLightSensor()
                restoreSystemBrightness()
            }
        }
        layout.addView(autoSwitch)
        layout.addView(createHint(getString(R.string.auto_brightness_hint)))

        dialog.setContentView(layout)
        dialog.show()
    }

    private fun createLabel(text: String, sizeSp: Float, bold: Boolean): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = sizeSp
        tv.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tv.setPadding(0, dp(14), 0, dp(4))
        return tv
    }

    private fun createHint(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 12f
        tv.setTextColor(0xFF888888.toInt())
        tv.setPadding(dp(4), 0, dp(4), dp(10))
        return tv
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()

    private fun dpFloat(value: Int): Float = resources.displayMetrics.density * value
}