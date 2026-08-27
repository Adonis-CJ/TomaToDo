package com.tomatodo.timer

import com.tomatodo.data.model.PomodoroType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 番茄钟计时控制器（进程级单例）。
 * 以 wall-clock 的 endAt 作为计时基准，锁屏 / 切后台 / 重组均不丢失精度。
 */
object TimerController {

    data class TimerState(
        val phase: PomodoroType = PomodoroType.FOCUS,
        val remainingMillis: Long = 0L,
        val totalMillis: Long = 0L,
        val isRunning: Boolean = false,
        val completedPomodoros: Int = 0,
        val taskId: Long? = null
    )

    sealed interface TimerEvent {
        data class PhaseCompleted(
            val phase: PomodoroType,
            val startAt: Long,
            val endAt: Long,
            val plannedMillis: Long,
            val actualMillis: Long
        ) : TimerEvent
    }

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TimerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TimerEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var endAt = 0L
    private var phaseStartedAt = 0L

    private var focusMillis = 25 * 60_000L
    private var shortMillis = 5 * 60_000L
    private var longMillis = 15 * 60_000L
    private var pomodorosBeforeLong = 4

    fun configure(focusMinutes: Int, shortMinutes: Int, longMinutes: Int, beforeLong: Int) {
        focusMillis = focusMinutes * 60_000L
        shortMillis = shortMinutes * 60_000L
        longMillis = longMinutes * 60_000L
        pomodorosBeforeLong = beforeLong.coerceAtLeast(1)
        val s = _state.value
        if (!s.isRunning && s.remainingMillis <= 0L) {
            _state.value = s.copy(
                phase = PomodoroType.FOCUS,
                totalMillis = focusMillis,
                remainingMillis = focusMillis
            )
        }
    }

    fun start(taskId: Long? = null) {
        val s = _state.value
        val remaining = if (s.remainingMillis > 0L) s.remainingMillis else phaseMillis(s.phase)
        if (phaseStartedAt == 0L) {
            phaseStartedAt = System.currentTimeMillis()
        }
        endAt = System.currentTimeMillis() + remaining
        _state.value = s.copy(
            isRunning = true,
            remainingMillis = remaining,
            totalMillis = if (s.totalMillis > 0L) s.totalMillis else phaseMillis(s.phase),
            taskId = taskId ?: s.taskId
        )
        startTicker()
    }

    fun pause() {
        val s = _state.value
        if (!s.isRunning) return
        _state.value = s.copy(
            isRunning = false,
            remainingMillis = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        )
        ticker?.cancel()
        ticker = null
    }

    fun reset() {
        ticker?.cancel()
        ticker = null
        endAt = 0L
        phaseStartedAt = 0L
        _state.value = TimerState(
            phase = PomodoroType.FOCUS,
            totalMillis = focusMillis,
            remainingMillis = focusMillis
        )
    }

    fun skip() {
        advancePhase()
    }

    private fun phaseMillis(phase: PomodoroType): Long = when (phase) {
        PomodoroType.FOCUS -> focusMillis
        PomodoroType.SHORT_BREAK -> shortMillis
        PomodoroType.LONG_BREAK -> longMillis
    }

    private fun advancePhase() {
        val s = _state.value
        val completedPhase = s.phase
        val completedStartAt = if (phaseStartedAt != 0L) phaseStartedAt else System.currentTimeMillis()
        val completedEndAt = System.currentTimeMillis()
        val completedPlanned = phaseMillis(completedPhase)

        val (nextPhase, newCompleted) = when (completedPhase) {
            PomodoroType.FOCUS -> {
                val c = s.completedPomodoros + 1
                val p = if (c % pomodorosBeforeLong == 0) PomodoroType.LONG_BREAK else PomodoroType.SHORT_BREAK
                p to c
            }
            else -> PomodoroType.FOCUS to s.completedPomodoros
        }
        val total = phaseMillis(nextPhase)
        ticker?.cancel()
        ticker = null
        endAt = 0L
        phaseStartedAt = 0L
        _state.value = s.copy(
            phase = nextPhase,
            completedPomodoros = newCompleted,
            totalMillis = total,
            remainingMillis = total,
            isRunning = false
        )
        scope.launch {
            _events.emit(
                TimerEvent.PhaseCompleted(
                    phase = completedPhase,
                    startAt = completedStartAt,
                    endAt = completedEndAt,
                    plannedMillis = completedPlanned,
                    actualMillis = completedPlanned
                )
            )
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val remaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    advancePhase()
                    break
                }
                _state.value = _state.value.copy(remainingMillis = remaining)
                delay(200)
            }
        }
    }
}
