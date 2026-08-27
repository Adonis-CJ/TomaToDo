package com.tomatodo.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.preferences.ThemeMode
import com.tomatodo.data.preferences.TimerSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as TomaTodoApplication).container.settingsPreferences

    val settings: StateFlow<TimerSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimerSettings())

    fun setFocus(m: Int) = viewModelScope.launch { prefs.setFocusMinutes(m) }
    fun setShort(m: Int) = viewModelScope.launch { prefs.setShortBreakMinutes(m) }
    fun setLong(m: Int) = viewModelScope.launch { prefs.setLongBreakMinutes(m) }
    fun setPomodoros(n: Int) = viewModelScope.launch { prefs.setPomodorosBeforeLongBreak(n) }
    fun setRingtone(id: String) = viewModelScope.launch { prefs.setRingtone(id) }
    fun setVolume(v: Float) = viewModelScope.launch { prefs.setVolume(v) }
    fun setVibrationOnly(v: Boolean) = viewModelScope.launch { prefs.setVibrationOnly(v) }
    fun setThemeMode(m: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(m) }
}
