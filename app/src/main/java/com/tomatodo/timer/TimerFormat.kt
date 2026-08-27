package com.tomatodo.timer

import com.tomatodo.data.model.PomodoroType
import java.util.Locale

fun formatCountdown(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}

fun phaseLabel(phase: PomodoroType): String = when (phase) {
    PomodoroType.FOCUS -> "专注"
    PomodoroType.SHORT_BREAK -> "短休息"
    PomodoroType.LONG_BREAK -> "长休息"
}
