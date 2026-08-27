package com.tomatodo.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.model.PomodoroSession
import com.tomatodo.data.model.PomodoroType
import com.tomatodo.data.model.Task
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StatsSummary(
    val todayFocusMinutes: Long = 0,
    val weekFocusMinutes: Long = 0,
    val totalPomodoros: Int = 0,
    val completedTasks: Int = 0,
    val totalTasks: Int = 0,
    val streakDays: Int = 0
) {
    val completionRate: Float
        get() = if (totalTasks == 0) 0f else completedTasks.toFloat() / totalTasks
}

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as TomaTodoApplication).container.database

    val stats: StateFlow<StatsSummary> = combine(
        db.pomodoroSessionDao().observeAll(),
        db.taskDao().observeAll()
    ) { sessions, tasks -> computeStats(sessions, tasks) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StatsSummary())
}

private fun computeStats(sessions: List<PomodoroSession>, tasks: List<Task>): StatsSummary {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli()

    val focus = sessions.filter { it.type == PomodoroType.FOCUS }
    val todayFocus = focus.filter { it.startAt >= todayStart }.sumOf { it.actualDuration }
    val weekFocus = focus.filter { it.startAt >= weekStart }.sumOf { it.actualDuration }
    val streak = computeStreak(focus.map { it.startAt }, zone)

    return StatsSummary(
        todayFocusMinutes = todayFocus,
        weekFocusMinutes = weekFocus,
        totalPomodoros = focus.size,
        completedTasks = tasks.count { it.isCompleted },
        totalTasks = tasks.size,
        streakDays = streak
    )
}

private fun computeStreak(startTimes: List<Long>, zone: ZoneId): Int {
    val days = startTimes.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
    if (days.isEmpty()) return 0
    var streak = 0
    var day = LocalDate.now(zone)
    if (day !in days) day = day.minusDays(1)
    while (day in days) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}
