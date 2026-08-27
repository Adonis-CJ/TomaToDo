package com.tomatodo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PomodoroType { FOCUS, SHORT_BREAK, LONG_BREAK }

/** 番茄会话（PRD §5.1） */
@Entity(tableName = "pomodoro_sessions")
data class PomodoroSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val type: PomodoroType,
    val startAt: Long,
    val endAt: Long,
    val plannedDuration: Long,     // 计划时长（分钟）
    val actualDuration: Long       // 实际时长（分钟）
)
