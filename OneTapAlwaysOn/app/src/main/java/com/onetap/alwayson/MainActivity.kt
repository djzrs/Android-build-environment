package com.onetap.alwayson

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.onetap.alwayson.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingStart = false
    private var suppressListener = false

    private val colorOptions = intArrayOf(
        0xFF3F51B5.toInt(), // 深蓝（默认）
        0xFFFF9800.toInt(), // 琥珀橙
        0xFF4CAF50.toInt(), // 绿色
        0xFFE53935.toInt(), // 红色
        0xFF9C27B0.toInt()  // 紫色
    )

    private val colorDots = mutableListOf<View>()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 无论是否授予，都不阻断主流程；正式版可按需提示
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.switchAlwaysOn.setOnCheckedChangeListener { _, isChecked ->
            if (suppressListener) return@setOnCheckedChangeListener
            if (isChecked) requestStart() else stopFloatBall()
        }

        setupColorPicker()
    }

    private fun setupColorPicker() {
        colorDots.addAll(
            listOf(
                binding.colorDot0, binding.colorDot1, binding.colorDot2,
                binding.colorDot3, binding.colorDot4
            )
        )
        val prefs = getSharedPreferences(FloatBallView.PREFS_NAME, MODE_PRIVATE)
        val savedColor = prefs.getInt(FloatBallView.KEY_BALL_COLOR, colorOptions[0])

        colorDots.forEachIndexed { index, dot ->
            val color = colorOptions[index]
            dot.background = createDotDrawable(color, selected = color == savedColor)
            dot.setOnClickListener {
                prefs.edit().putInt(FloatBallView.KEY_BALL_COLOR, color).apply()
                colorDots.forEachIndexed { i, d ->
                    d.background = createDotDrawable(colorOptions[i], selected = i == index)
                }
                sendColorToService(color)
            }
        }
    }

    private fun createDotDrawable(color: Int, selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (selected) {
                setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
            }
        }

    private fun sendColorToService(color: Int) {
        val prefs = getSharedPreferences(FloatBallView.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(FloatBallView.KEY_SERVICE_RUNNING, false)) {
            startService(
                Intent(this, FloatBallService::class.java)
                    .setAction(FloatBallService.ACTION_UPDATE_COLOR)
                    .putExtra(FloatBallService.EXTRA_COLOR, color)
            )
        }
    }

    private fun requestStart() {
        if (!Settings.canDrawOverlays(this)) {
            pendingStart = true
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        maybeRequestNotificationPermission()
        startFloatBall()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startFloatBall() {
        val intent = Intent(this, FloatBallService::class.java)
            .setAction(FloatBallService.ACTION_SHOW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun stopFloatBall() {
        pendingStart = false
        stopService(Intent(this, FloatBallService::class.java))
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        if (!pendingStart) return
        if (Settings.canDrawOverlays(this)) {
            pendingStart = false
            maybeRequestNotificationPermission()
            startFloatBall()
        } else {
            // 用户从授权页返回但未授予，回滚开关
            pendingStart = false
            setSwitchSilently(false)
        }
    }

    private fun setSwitchSilently(checked: Boolean) {
        suppressListener = true
        binding.switchAlwaysOn.isChecked = checked
        suppressListener = false
    }
}