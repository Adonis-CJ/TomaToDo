package com.tomatodo.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.model.PomodoroSession
import com.tomatodo.data.model.PomodoroType
import com.tomatodo.data.model.Subject
import com.tomatodo.data.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 统计范围（OPTIMIZATION §8.3） */
enum class StatsRange(val label: String) { DAY("日"), WEEK("周"), MONTH("月") }

data class DailyFocus(val date: LocalDate, val minutes: Long)

data class SubjectShare(val subject: Subject?, val minutes: Long, val fraction: Float)

data class SubjectCompletion(val subject: Subject?, val done: Int, val total: Int)

data class StatsUiState(
    val range: StatsRange = StatsRange.WEEK,
    val rangeFocusMinutes: Long = 0,
    val todayMinutes: Long = 0,
    val totalPomodoros: Int = 0,
    val streakDays: Int = 0,
    val dailyFocus: List<DailyFocus> = emptyList(),   // 近 30 日（柱状图）
    val heatmapWeeks: List<List<Double>> = emptyList(), // V4：12 周归一化热力图（周列 x 日行）
    val subjectShares: List<SubjectShare> = emptyList(), // 当前范围科目分布
    val completionBySubject: List<SubjectCompletion> = emptyList()
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as TomaTodoApplication).container.database

    private val _range = MutableStateFlow(StatsRange.WEEK)
    val range: StateFlow<StatsRange> = _range.asStateFlow()

    fun setRange(r: StatsRange) { _range.value = r }

    /** 近 30 日专注 CSV 导出（OPTIMIZATION §8.4） */
    fun exportCsv(uri: android.net.Uri) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder("日期,专注分钟\n")
            stats.value.dailyFocus.forEach { sb.append("${it.date},${it.minutes}\n") }
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
            }
        }
    }

    val stats: StateFlow<StatsUiState> = combine(
        db.pomodoroSessionDao().observeAll(),
        db.taskDao().observeAll(),
        db.subjectDao().observeAll(),
        _range
    ) { sessions, tasks, subjects, range ->
        computeStats(sessions.filter { it.type == PomodoroType.FOCUS }, tasks, subjects, range)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StatsUiState())
}

private fun computeStats(
    focus: List<PomodoroSession>,
    tasks: List<Task>,
    subjects: List<Subject>,
    range: StatsRange
): StatsUiState {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    fun dayOf(epoch: Long): LocalDate = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()

    // 每日专注分钟（近 30 日）
    val byDay = focus.groupBy { dayOf(it.startAt) }
        .mapValues { (_, list) -> list.sumOf { it.actualDuration } }
    val dailyFocus = (29 downTo 0).map { offset ->
        val d = today.minusDays(offset.toLong())
        DailyFocus(d, byDay[d] ?: 0L)
    }

    // 当前范围
    val rangeStart = when (range) {
        StatsRange.DAY -> today
        StatsRange.WEEK -> today.with(DayOfWeek.MONDAY)
        StatsRange.MONTH -> today.withDayOfMonth(1)
    }
    val rangeFocus = focus.filter { !dayOf(it.startAt).isBefore(rangeStart) }
        .sumOf { it.actualDuration }

    // 科目分布（当前范围）
    val subjectById = subjects.associateBy { it.id }
    val rangeBySubject = rangeFocus.let { total ->
        focus.filter { !dayOf(it.startAt).isBefore(rangeStart) }
            .groupBy { it.subjectId }
            .map { (id, list) ->
                val minutes = list.sumOf { it.actualDuration }
                SubjectShare(
                    id?.let { subjectById[it] },
                    minutes,
                    (minutes.toDouble() / total.coerceAtLeast(1)).toFloat()
                )
            }
            .sortedByDescending { it.minutes }
    }

    // 按科目完成率
    val completion = tasks.filter { it.subjectId != null }
        .groupBy { it.subjectId }
        .map { (id, list) ->
            SubjectCompletion(subjectById[id], list.count { it.isCompleted }, list.size)
        }
        .sortedByDescending { it.total }

    // 连续专注
    val focusDays = focus.map { dayOf(it.startAt) }.toSet()
    var streak = 0
    var cursor = today
    if (cursor !in focusDays) cursor = cursor.minusDays(1)
    while (cursor in focusDays) {
        streak++
        cursor = cursor.minusDays(1)
    }

    // V4 热力图：近 12 周，按周一对齐，归一化到 0..1
    val heatmapStart = today.minusDays(83)
    val alignedStart = heatmapStart.minusDays((heatmapStart.dayOfWeek.value - 1).toLong())
    val maxMinutes = (byDay.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val heatmapWeeks = buildList {
        var weekStart = alignedStart
        while (!weekStart.isAfter(today)) {
            add((0 until 7).map { offset ->
                val d = weekStart.plusDays(offset.toLong())
                if (d.isAfter(today)) -1.0
                else ((byDay[d] ?: 0L).toDouble() / maxMinutes)
            })
            weekStart = weekStart.plusWeeks(1)
        }
    }

    return StatsUiState(
        range = range,
        rangeFocusMinutes = rangeFocus,
        todayMinutes = byDay[today] ?: 0L,
        totalPomodoros = focus.size,
        streakDays = streak,
        dailyFocus = dailyFocus,
        heatmapWeeks = heatmapWeeks,
        subjectShares = rangeBySubject,
        completionBySubject = completion
    )
}
