package com.onetap.alwayson

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.onetap.alwayson.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingStart = false
    private var suppressListener = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 骨架阶段：无论是否授予，都不阻断主流程；正式版可按需提示
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.switchAlwaysOn.setOnCheckedChangeListener { _, isChecked ->
            if (suppressListener) return@setOnCheckedChangeListener
            if (isChecked) requestStart() else stopFloatBall()
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