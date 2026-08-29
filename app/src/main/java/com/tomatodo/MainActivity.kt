package com.tomatodo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tomatodo.data.preferences.ThemeMode
import com.tomatodo.timer.TimerController
import com.tomatodo.ui.MainScreen
import com.tomatodo.ui.theme.TomaTodoTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        requestNotificationPermissionIfNeeded()
        keepScreenOnWhileTiming()

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

    override fun onResume() {
        super.onResume()
        // 从权限设置页等其他界面返回后恢复全屏
        hideSystemBars()
    }

    /** 全局全屏（v1.5 §1）：隐藏状态栏 + 导航栏，边缘轻扫临时唤出并自动隐藏 */
    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** 计时防睡眠（v1.5 §3）：前台运行中保持亮屏，暂停/结束即归还系统默认息屏策略 */
    private fun keepScreenOnWhileTiming() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TimerController.state.collect { st ->
                    if (st.isRunning) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
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
