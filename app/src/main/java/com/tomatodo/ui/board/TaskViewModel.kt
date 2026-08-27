package com.tomatodo.ui.board

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.model.Subject
import com.tomatodo.data.model.Task
import com.tomatodo.data.model.TaskStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 看板 ViewModel（OPTIMIZATION §7）：日期导航（历史/未来任意日）+ 任务状态直达 + 撤销删除。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as TomaTodoApplication).container
    private val taskDao = container.database.taskDao()
    private val subjectDao = container.database.subjectDao()

    val subjects: Flow<List<Subject>> = subjectDao.observeAll()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    data class BoardState(
        val date: LocalDate = LocalDate.now(),
        val isToday: Boolean = true,
        val tasks: List<Task> = emptyList()
    ) {
        val doneCount: Int get() = tasks.count { it.isCompleted }
    }

    /** 所选日期的看板状态（按任务 startTime 归属日） */
    val boardState: StateFlow<BoardState> = _selectedDate.flatMapLatest { date ->
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        taskDao.observeByDate(from, to).map { tasks ->
            BoardState(date = date, isToday = date == LocalDate.now(), tasks = tasks)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BoardState())

    // ---- 日期导航 ----

    fun selectDate(date: LocalDate) { _selectedDate.value = date }
    fun shiftDate(delta: Long) { _selectedDate.update { it.plusDays(delta) } }
    fun backToToday() { _selectedDate.value = LocalDate.now() }

    // ---- 任务操作 ----

    fun addTask(content: String, startTime: Long, endTime: Long, subjectId: Long?) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            taskDao.upsert(
                Task(
                    content = content.trim(),
                    startTime = startTime,
                    endTime = endTime,
                    subjectId = subjectId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun updateTask(task: Task) = viewModelScope.launch {
        taskDao.upsert(task.copy(updatedAt = System.currentTimeMillis()))
    }

    /** 直改状态（勾选框 / 编辑弹窗） */
    fun setStatus(task: Task, status: TaskStatus) = viewModelScope.launch {
        taskDao.updateStatus(task.id, status, status == TaskStatus.DONE, System.currentTimeMillis())
    }

    /** 勾选框：非完成 -> 完成（直达）；完成 -> 待办（撤销完成） */
    fun toggleDone(task: Task) {
        setStatus(task, if (task.isCompleted) TaskStatus.TODO else TaskStatus.DONE)
    }

    fun markDoing(taskId: Long) = viewModelScope.launch {
        taskDao.updateStatus(taskId, TaskStatus.DOING, false, System.currentTimeMillis())
    }

    fun delete(task: Task) = viewModelScope.launch { taskDao.delete(task.id) }

    /** 撤销删除：按原 id 恢复 */
    fun restore(task: Task) = viewModelScope.launch { taskDao.upsert(task) }
}
