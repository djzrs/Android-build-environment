package com.onetap.alwayson

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class FloatBallService : Service() {

    companion object {
        const val ACTION_SHOW = "com.onetap.alwayson.action.SHOW"
        const val ACTION_HIDE = "com.onetap.alwayson.action.HIDE"
        const val ACTION_UPDATE_COLOR = "com.onetap.alwayson.action.UPDATE_COLOR"
        const val EXTRA_COLOR = "extra_color"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val DEFAULT_DURATION_MINUTES = 15
        private const val UNLIMITED_MAX_MINUTES = 12 * 60

        private const val CHANNEL_ID = "float_ball_channel"
        private const val NOTIFICATION_ID = 1001
        private const val REMINDER_NOTIFICATION_ID = 1002
    }

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private var floatBallView: FloatBallView? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    private var durationMinutes = DEFAULT_DURATION_MINUTES
    private var totalMillis = 0L
    private var endTimeMillis = 0L
    private var oneMinuteReminded = false
    private var stopped = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()
        registerScreenOffReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                durationMinutes = intent.getIntExtra(
                    EXTRA_DURATION_MINUTES,
                    getSharedPreferences(FloatBallView.PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt(FloatBallView.KEY_DURATION_MINUTES, DEFAULT_DURATION_MINUTES)
                )
                startAsForeground()
                showBall()
                acquireWakeLock()
                startCountdown()
                setServiceRunning(true)
            }
            ACTION_HIDE -> {
                stopEverything()
            }
            ACTION_UPDATE_COLOR -> {
                val color = intent.getIntExtra(EXTRA_COLOR, 0)
                floatBallView?.setColor(color)
            }
        }
        return START_NOT_STICKY
    }

    // ---------- WakeLock：跨应用常亮 ----------

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        releaseWakeLock()
        val powerManager = getSystemService(PowerManager::class.java)
        val timeoutMillis = if (durationMinutes > 0) {
            durationMinutes * 60_000L
        } else {
            UNLIMITED_MAX_MINUTES * 60_000L
        }
        val lock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
            "OneTapAlwaysOn:keep_screen_on"
        )
        // 合规要求：永远带超时获取，不提供无保护的无限持有
        lock.acquire(timeoutMillis)
        wakeLock = lock
        totalMillis = timeoutMillis
        endTimeMillis = System.currentTimeMillis() + timeoutMillis
        oneMinuteReminded = false
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            runCatching { if (lock.isHeld) lock.release() }
            wakeLock = null
        }
    }

    // ---------- 定时倒计时 ----------

    private fun startCountdown() {
        mainHandler.removeCallbacksAndMessages(null)
        val runnable = object : Runnable {
            private var tick = 0
            override fun run() {
                val remaining = endTimeMillis - System.currentTimeMillis()
                if (remaining <= 0) {
                    stopEverything()
                    return
                }
                tick++
                // 通知每 10 秒刷新一次；最后 60 秒每秒刷新以保持提醒
                if (tick % 10 == 0 || remaining <= 60_000) {
                    updateCountdownNotification(remaining)
                }
                floatBallView?.updateCountdown(remaining, totalMillis)
                mainHandler.postDelayed(this, 1000L)
            }
        }
        mainHandler.post(runnable)
        countdownRunnable = runnable
    }

    private fun updateCountdownNotification(remainingMillis: Long) {
        val minutes = ((remainingMillis + 59_000) / 60_000).toInt()
        val text = if (durationMinutes > 0) {
            getString(R.string.notification_countdown, minutes)
        } else {
            getString(R.string.notification_unlimited)
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))

        // 到点前 1 分钟提醒一次
        if (durationMinutes > 0 && remainingMillis <= 60_000 && !oneMinuteReminded) {
            oneMinuteReminded = true
            val reminder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_almost_done))
                .setAutoCancel(true)
                .build()
            notificationManager.notify(REMINDER_NOTIFICATION_ID, reminder)
        }
    }

    // ---------- 锁屏即释放 ----------

    private fun registerScreenOffReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                stopEverything()
            }
        }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver!!,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
            screenOffReceiver = null
        }
    }

    // ---------- 前台服务与悬浮球 ----------

    private fun startAsForeground() {
        val notification = buildNotification(
            if (durationMinutes > 0) {
                getString(R.string.notification_countdown, durationMinutes)
            } else {
                getString(R.string.notification_unlimited)
            }
        )
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

    private fun setServiceRunning(running: Boolean) {
        getSharedPreferences(FloatBallView.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(FloatBallView.KEY_SERVICE_RUNNING, running)
            .apply()
    }

    private fun stopEverything() {
        if (stopped) return
        stopped = true
        mainHandler.removeCallbacksAndMessages(null)
        countdownRunnable = null
        releaseWakeLock()
        removeBall()
        stopForeground(STOP_FOREGROUND_REMOVE)
        setServiceRunning(false)
        stopSelf()
    }

    override fun onDestroy() {
        stopEverything()
        unregisterScreenOffReceiver()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
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
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notification_hide), hideIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}