package com.tomatodo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskStatus { TODO, DOING, DONE }

/** 任务（PRD §5.1） */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val startTime: Long,          // epoch ms，默认当前时间
    val endTime: Long,            // epoch ms，自定义
    val subjectId: Long? = null,
    val status: TaskStatus = TaskStatus.TODO,
    val isCompleted: Boolean = false,
    val pomodoroCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
