package com.djzrs.htmlmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.djzrs.htmlmanager.navigation.AppNavHost
import com.djzrs.htmlmanager.navigation.NavBridge
import com.djzrs.htmlmanager.navigation.Routes
import com.djzrs.htmlmanager.ui.theme.HtmlManagerTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+ 前台服务的通知要用户授权，没有的话服务照跑但看不到通知。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            HtmlManagerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // 回到前台：收起悬浮窗。
        AppVisibility.setForeground(true)
    }

    override fun onStop() {
        // 切到后台且服务器在跑：挂出悬浮窗，服务本身继续跑。
        AppVisibility.setForeground(false)
        super.onStop()
    }

    /** 通知栏、悬浮窗点进来时直接落到服务器页。 */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SERVER, false) != true) return
        intent.removeExtra(EXTRA_OPEN_SERVER)
        NavBridge.request(Routes.SERVER)
    }

    companion object {
        const val EXTRA_OPEN_SERVER = "com.djzrs.htmlmanager.extra.OPEN_SERVER"
    }
}
