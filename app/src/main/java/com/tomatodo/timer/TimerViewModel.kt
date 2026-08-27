package com.tomatodo.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as TomaTodoApplication).container.settingsPreferences

    val state = TimerController.state
    val events = TimerController.events

    init {
        viewModelScope.launch {
            prefs.settings.collect { s ->
                TimerController.configure(
                    focusMinutes = s.focusMinutes,
                    shortMinutes = s.shortBreakMinutes,
                    longMinutes = s.longBreakMinutes,
                    beforeLong = s.pomodorosBeforeLongBreak
                )
            }
        }
    }

    fun start() {
        TimerController.start()
        TimerService.start(getApplication())
        FloatingWindowManager.show(getApplication())
    }

    fun pause() = TimerController.pause()

    fun reset() {
        TimerController.reset()
        TimerService.stop(getApplication())
        FloatingWindowManager.hide()
    }

    fun skip() = TimerController.skip()
}
