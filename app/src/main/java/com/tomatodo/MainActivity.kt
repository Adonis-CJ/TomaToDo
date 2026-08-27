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
}
