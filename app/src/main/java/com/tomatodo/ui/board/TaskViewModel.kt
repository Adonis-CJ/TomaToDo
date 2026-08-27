package com.tomatodo.ui.board

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.model.Subject
import com.tomatodo.data.model.Task
import com.tomatodo.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as TomaTodoApplication).container
    private val taskDao = container.database.taskDao()
    private val subjectDao = container.database.subjectDao()

    val tasks: Flow<List<Task>> = taskDao.observeAll()
    val subjects: Flow<List<Subject>> = subjectDao.observeAll()

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

    fun advanceStatus(task: Task) {
        val next = when (task.status) {
            TaskStatus.TODO -> TaskStatus.DOING
            TaskStatus.DOING -> TaskStatus.DONE
            TaskStatus.DONE -> TaskStatus.TODO
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            taskDao.updateStatus(task.id, next, next == TaskStatus.DONE, now)
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch { taskDao.delete(task.id) }
    }
}
