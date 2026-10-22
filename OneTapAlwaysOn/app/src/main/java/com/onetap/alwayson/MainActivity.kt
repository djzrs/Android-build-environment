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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.onetap.alwayson.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTO_START = "com.onetap.alwayson.extra.AUTO_START"
        const val EXTRA_DURATION_MINUTES = "com.onetap.alwayson.extra.DURATION_MINUTES"
    }

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
            // 通知权限结果不阻断主流程；倒计时提醒依赖该权限
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.switchAlwaysOn.setOnCheckedChangeListener { _, isChecked ->
            if (suppressListener) return@setOnCheckedChangeListener
            if (isChecked) requestStart() else stopFloatBall()
        }

        setupDurationPicker()
        setupColorPicker()
        handleShortcutIntent(intent)
    }

    // ---------- 时长选择 ----------

    private fun setupDurationPicker() {
        val prefs = getSharedPreferences(FloatBallView.PREFS_NAME, MODE_PRIVATE)
        val savedMinutes = prefs.getInt(FloatBallView.KEY_DURATION_MINUTES, FloatBallService.DEFAULT_DURATION_MINUTES)
        when (savedMinutes) {
            30 -> binding.radio30.isChecked = true
            60 -> binding.radio60.isChecked = true
            0 -> binding.radioUnlimited.isChecked = true
            else -> binding.radio15.isChecked = true
        }
        updateModeHint(savedMinutes)

        binding.radioGroupDuration.setOnCheckedChangeListener { _, checkedId ->
            val minutes = when (checkedId) {
                binding.radio30.id -> 30
                binding.radio60.id -> 60
                binding.radioUnlimited.id -> 0
                else -> 15
            }
            prefs.edit().putInt(FloatBallView.KEY_DURATION_MINUTES, minutes).apply()
            updateModeHint(minutes)
        }
    }

    private fun updateModeHint(minutes: Int) {
        val resId = when (minutes) {
            30 -> R.string.mode_hint_30
            60 -> R.string.mode_hint_60
            0 -> R.string.mode_hint_unlimited
            else -> R.string.mode_hint_15
        }
        binding.tvModeHint.setText(resId)
    }

    private fun currentDurationMinutes(): Int {
        val prefs = getSharedPreferences(FloatBallView.PREFS_NAME, MODE_PRIVATE)
        return prefs.getInt(FloatBallView.KEY_DURATION_MINUTES, FloatBallService.DEFAULT_DURATION_MINUTES)
    }

    // ---------- 颜色选择 ----------

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

    // ---------- 开关联动 ----------

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
        showWakeLockHintIfNeeded {
            startFloatBall()
        }
    }

    private fun showWakeLockHintIfNeeded(onConfirmed: () -> Unit) {
        val prefs = getSharedPreferences(FloatBallView.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(FloatBallView.KEY_WAKE_LOCK_HINT_SHOWN, false)) {
            onConfirmed()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.wakelock_dialog_title)
            .setMessage(R.string.wakelock_dialog_message)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ ->
                prefs.edit().putBoolean(FloatBallView.KEY_WAKE_LOCK_HINT_SHOWN, true).apply()
                onConfirmed()
            }
            .show()
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
            .putExtra(FloatBallService.EXTRA_DURATION_MINUTES, currentDurationMinutes())
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

    // ---------- 快捷方式入口 ----------

    private fun handleShortcutIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTO_START, false) != true) return
        val minutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, FloatBallService.DEFAULT_DURATION_MINUTES)
        getSharedPreferences(FloatBallView.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(FloatBallView.KEY_DURATION_MINUTES, minutes)
            .apply()
        when (minutes) {
            30 -> binding.radio30.isChecked = true
            60 -> binding.radio60.isChecked = true
            0 -> binding.radioUnlimited.isChecked = true
            else -> binding.radio15.isChecked = true
        }
        updateModeHint(minutes)
        // 自动开启
        binding.switchAlwaysOn.isChecked = true
    }

    // ---------- 生命周期 ----------

    override fun onResume() {
        super.onResume()
        if (!pendingStart) {
            syncSwitchWithService()
            return
        }
        if (Settings.canDrawOverlays(this)) {
            pendingStart = false
            maybeRequestNotificationPermission()
            showWakeLockHintIfNeeded {
                startFloatBall()
            }
        } else {
            // 用户从授权页返回但未授予，回滚开关
            pendingStart = false
            setSwitchSilently(false)
        }
    }

    private fun syncSwitchWithService() {
        val running = getSharedPreferences(FloatBallView.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(FloatBallView.KEY_SERVICE_RUNNING, false)
        if (!running && binding.switchAlwaysOn.isChecked) {
            setSwitchSilently(false)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setSwitchSilently(checked: Boolean) {
        suppressListener = true
        binding.switchAlwaysOn.isChecked = checked
        suppressListener = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }
}