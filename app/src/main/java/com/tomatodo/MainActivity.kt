package com.tomatodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.tomatodo.data.preferences.ThemeMode
import com.tomatodo.ui.MainScreen
import com.tomatodo.ui.theme.TomaTodoTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        val app = application as TomaTodoApplication
        setContent {
            val themeMode by remember(app) {
                app.container.settingsPreferences.settings.map { it.themeMode }
            }.collectAsState(initial = ThemeMode.SYSTEM)

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            TomaTodoTheme(darkTheme = darkTheme) {
                MainScreen()
            }
        }
    }

    /** 通知权限（OPTIMIZATION 技术债 #5）：Android 13+ 动态申请 */
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    /** 悬浮窗仅在应用进入后台且计时进行时出现（用户反馈） */
    override fun onStop() {
        super.onStop()
        com.tomatodo.timer.AppForegroundTracker.isForeground = false
        if (com.tomatodo.timer.TimerController.state.value.isRunning) {
            com.tomatodo.timer.FloatingWindowManager.show(this)
        }
    }

    override fun onStart() {
        super.onStart()
        com.tomatodo.timer.AppForegroundTracker.isForeground = true
        // 回到应用即停掉「完成提醒」的循环响铃通知
        com.tomatodo.timer.AlarmNotifications.cancel(this)
        com.tomatodo.timer.FloatingWindowManager.hide()
    }
}
