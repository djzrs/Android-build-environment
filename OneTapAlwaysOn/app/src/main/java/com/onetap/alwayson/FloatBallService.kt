package com.onetap.alwayson

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class FloatBallService : Service() {

    companion object {
        const val ACTION_SHOW = "com.onetap.alwayson.action.SHOW"
        const val ACTION_HIDE = "com.onetap.alwayson.action.HIDE"
        const val ACTION_UPDATE_COLOR = "com.onetap.alwayson.action.UPDATE_COLOR"
        const val EXTRA_COLOR = "extra_color"
        private const val CHANNEL_ID = "float_ball_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var floatBallView: FloatBallView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                startAsForeground()
                showBall()
                getSharedPreferences(FloatBallView.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(FloatBallView.KEY_SERVICE_RUNNING, true)
                    .apply()
            }
            ACTION_HIDE -> {
                removeBall()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE_COLOR -> {
                val color = intent.getIntExtra(EXTRA_COLOR, 0)
                floatBallView?.setColor(color)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showBall() {
        if (floatBallView != null) return
        val view = FloatBallView(this, windowManager)
        windowManager.addView(view, view.windowParams)
        floatBallView = view
    }

    private fun removeBall() {
        floatBallView?.let { view ->
            runCatching { windowManager.removeView(view) }
            floatBallView = null
        }
    }

    override fun onDestroy() {
        getSharedPreferences(FloatBallView.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(FloatBallView.KEY_SERVICE_RUNNING, false)
            .apply()
        removeBall()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val hideIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatBallService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)   // 骨架占位，正式版换专用图标
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notification_hide), hideIntent)
            .setOngoing(true)
            .build()
    }
}