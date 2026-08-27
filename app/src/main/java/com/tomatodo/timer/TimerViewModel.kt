package com.tomatodo.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.model.TaskStatus
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as TomaTodoApplication).container.settingsPreferences
    private val taskDao = (application as TomaTodoApplication).container.database.taskDao()

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

    /** 从看板卡片一键开始番茄：关联任务 + 置为进行中 + 启动服务与悬浮窗 */
    fun startForTask(taskId: Long) {
        TimerController.start(taskId)
        TimerService.start(getApplication())
        FloatingWindowManager.show(getApplication())
        viewModelScope.launch {
            taskDao.updateStatus(taskId, TaskStatus.DOING, false, System.currentTimeMillis())
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
