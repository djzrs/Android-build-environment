package com.djzrs.htmlmanager.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.djzrs.htmlmanager.AppVisibility
import com.djzrs.htmlmanager.MainActivity
import com.djzrs.htmlmanager.R
import com.djzrs.htmlmanager.data.FileRepository
import com.djzrs.htmlmanager.util.NetworkUtils

/**
 * 局域网服务器跑在前台服务里：APP 退到后台、Activity 被回收都不影响访问。
 * 只有用户点「停止」或通知栏的「停止」才会结束。
 */
class ServerService : Service() {

    private var server: AppHttpServer? = null
    private var overlay: ServerOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        overlay = ServerOverlay(this)
        serviceInstance = this
        AppVisibility.observe { visible -> onAppVisibilityChanged(visible) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServer()
            return START_NOT_STICKY
        }
        // 首次启动与系统重建（intent 为 null）都走这里：先占住前台，再起服务器。
        // 已在运行时沿用已有地址，别把通知里的地址冲掉。
        val text = ServerBridge.state.value.address.ifBlank { getString(R.string.server_notification_starting) }
        startForeground(NOTIFICATION_ID, buildNotification(text))
        if (server == null) startServer()
        return START_STICKY
    }

    override fun onDestroy() {
        overlay?.hide()
        runCatching { server?.stop() }
        server = null
        ServerBridge.reset()
        if (serviceInstance === this) serviceInstance = null
        AppVisibility.clearObserver()
        super.onDestroy()
    }

    /** 切后台挂悬浮窗，回前台收起；服务没在跑就什么都不做。 */
    private fun onAppVisibilityChanged(visible: Boolean) {
        val address = ServerBridge.state.value.address
        if (visible) {
            overlay?.hide()
            overlay?.resetDismiss()
        } else if (server != null) {
            overlay?.show(address)
        }
    }

    private fun startServer() {
        val nano = AppHttpServer(FileRepository(this).directory)
        val started = runCatching { nano.start(SOCKET_READ_TIMEOUT, true) }.isSuccess
        if (!started) {
            runCatching { nano.stop() }
            ServerBridge.fail("端口 ${AppHttpServer.PORT} 被占用")
            stopForegroundCompat()
            stopSelf()
            return
        }

        server = nano
        val lanIp = NetworkUtils.localIpv4Address()
        val address = "http://${lanIp ?: LOOPBACK_HOST}:${AppHttpServer.PORT}"
        ServerBridge.publish(address, isLoopback = lanIp == null)

        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(address))

        // 服务被系统重建时 APP 可能已在后台，直接挂回悬浮窗。
        if (!AppVisibility.isForeground) overlay?.show(address)
    }

    private fun stopServer() {
        overlay?.hide()
        runCatching { server?.stop() }
        server = null
        ServerBridge.reset()
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(content: String): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SERVER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getForegroundService(
            this,
            REQUEST_STOP,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.server_notification_title))
            .setContentText(content)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.server_notification_action_stop), stop)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.server_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.server_notification_channel_desc)
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "html_manager_server"
        private const val NOTIFICATION_ID = 8080
        private const val REQUEST_OPEN = 1
        private const val REQUEST_STOP = 2
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val SOCKET_READ_TIMEOUT = 5000

        private const val ACTION_START = "com.djzrs.htmlmanager.SERVER_START"
        private const val ACTION_STOP = "com.djzrs.htmlmanager.SERVER_STOP"

        /** 服务活着时才有值，用来判断是否真有一个服务可以停。 */
        @Volatile
        private var serviceInstance: ServerService? = null

        fun start(context: Context) {
            val intent = Intent(context, ServerService::class.java).setAction(ACTION_START)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            // 服务实例还在就让它自己收尾；不在了就直接清状态，别去拉起一个空服务。
            val alive = serviceInstance != null
            if (alive) {
                runCatching {
                    context.startService(Intent(context, ServerService::class.java).setAction(ACTION_STOP))
                }
            }
            if (!alive) ServerBridge.reset()
        }
    }
}
