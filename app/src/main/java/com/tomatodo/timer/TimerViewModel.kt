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

    /** 悬浮窗权限缺失（OPTIMIZATION 技术债 #6）：置位后由 UI 弹引导 */
    val needOverlayPermission = kotlinx.coroutines.flow.MutableStateFlow(false)

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
        tryShowFloatingWindow()
        viewModelScope.launch {
            taskDao.updateStatus(taskId, TaskStatus.DOING, false, System.currentTimeMillis())
        }
    }

    fun start() {
        TimerController.start()
        TimerService.start(getApplication())
        tryShowFloatingWindow()
    }

    private fun tryShowFloatingWindow() {
        if (android.provider.Settings.canDrawOverlays(getApplication())) {
            FloatingWindowManager.show(getApplication())
        } else {
            needOverlayPermission.value = true
        }
    }

    fun pause() = TimerController.pause()

    fun reset() {
        TimerController.reset()
        TimerService.stop(getApplication())
        FloatingWindowManager.hide()
    }

    fun skip() = TimerController.skip()
}
