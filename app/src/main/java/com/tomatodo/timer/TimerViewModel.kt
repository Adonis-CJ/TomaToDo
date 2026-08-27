package com.tomatodo.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.model.TaskStatus
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as TomaTodoApplication).container.settingsPreferences
    private val taskDao = (application as TomaTodoApplication).container.database.taskDao()

    val state = TimerController.state
    val events = TimerController.events

    /** 未完成任务列表（计时页任务选择器） */
    val activeTasks = taskDao.observeAll()

    /** 换绑当前任务 */
    fun bindTask(taskId: Long?) = TimerController.bindTask(taskId)

    /** 切换沉浸模式（番茄页内直接切换翻页钟 / 背景图时钟） */
    fun setImmersionMode(mode: com.tomatodo.data.preferences.ImmersionMode) =
        viewModelScope.launch { prefs.setImmersionMode(mode) }

    /** 设置（沉浸模式 / 壁纸等），供计时页读取 */
    val settings = prefs.settings.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        com.tomatodo.data.preferences.TimerSettings()
    )

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

    /** 从看板卡片一键开始番茄：关联任务 + 置为进行中 + 启动服务（悬浮窗仅应用后台时出现） */
    fun startForTask(taskId: Long) {
        TimerController.start(taskId)
        TimerService.start(getApplication())
        viewModelScope.launch {
            taskDao.updateStatus(taskId, TaskStatus.DOING, false, System.currentTimeMillis())
        }
    }

    fun start() {
        TimerController.start()
        TimerService.start(getApplication())
    }

    fun pause() = TimerController.pause()

    fun reset() {
        TimerController.reset()
        TimerService.stop(getApplication())
    }

    fun skip() = TimerController.skip()
}
