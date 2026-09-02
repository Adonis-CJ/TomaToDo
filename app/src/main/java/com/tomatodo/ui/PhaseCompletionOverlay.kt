package com.tomatodo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomatodo.data.model.PomodoroType
import com.tomatodo.timer.AlarmNotifications
import com.tomatodo.timer.TimerController
import com.tomatodo.timer.TimerViewModel
import com.tomatodo.ui.theme.AppSerif
import com.tomatodo.ui.theme.Motion
import kotlinx.coroutines.delay

private data class CompletionInfo(val completedPhase: PomodoroType, val completedMinutes: Long)

/**
 * 番茄钟阶段完成全屏提醒（v1.2「结束醒目」应用内部分）：
 * 脉冲圆环 + 大字提示 + 直接进入下一阶段的按钮；轻触任意处关闭；30s 无操作自动消失。
 * 后台场景由 TimerService 的 ALARM 通知（循环响铃）接管，回到前台即取消。
 */
@Composable
fun PhaseCompletionOverlay(
    timerViewModel: TimerViewModel,
    visible: Boolean
) {
    var completion by remember { mutableStateOf<CompletionInfo?>(null) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val visibleNow by androidx.compose.runtime.rememberUpdatedState(visible)

    LaunchedEffect(Unit) {
        timerViewModel.events.collect { e ->
            if (e is TimerController.TimerEvent.PhaseCompleted && visibleNow &&
                com.tomatodo.timer.AppForegroundTracker.isForeground
            ) {
                completion = CompletionInfo(e.phase, e.plannedMillis / 60_000L)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                AlarmNotifications.cancel(context)
            }
        }
    }

    // 30s 无操作自动消失（不会永久遮挡其他页面）
    LaunchedEffect(completion) {
        if (completion != null) {
            delay(30_000)
            completion = null
        }
    }

    val state by timerViewModel.state.collectAsState()
    // 退出淡出期间仍需内容渲染：保留最后一次非空信息（赋值放 LaunchedEffect，组合期不做副作用）
    var lastInfo by remember { mutableStateOf<CompletionInfo?>(null) }
    LaunchedEffect(completion) { if (completion != null) lastInfo = completion }

    AnimatedVisibility(
        visible = completion != null,
        enter = fadeIn(Motion.enter()),
        exit = fadeOut(Motion.exit())
    ) {
        val info = completion ?: lastInfo
        if (info != null) {
            val focusDone = info.completedPhase == PomodoroType.FOCUS
            val nextMinutes = state.totalMillis / 60_000L

            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { completion = null }
            ) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 脉冲圆环
                    Box(contentAlignment = Alignment.Center) {
                        PulseRings(color = MaterialTheme.colorScheme.primary)
                        Box(
                            Modifier
                                .size(112.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        if (focusDone) "专注完成！" else "休息完成！",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppSerif,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (focusDone) {
                            "已完成 ${state.completedPomodoros} 个番茄 · 休息 $nextMinutes 分钟"
                        } else {
                            "休息了 ${info.completedMinutes} 分钟 · 开始下一个专注吧"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = {
                            timerViewModel.start()
                            completion = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(
                            if (focusDone) "开始休息" else "开始专注",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { completion = null }) {
                        Text("稍后再说", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 两层错相位的呼吸圆环 */
@Composable
private fun PulseRings(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    repeat(2) { ring ->
        val delayMs = ring * 700
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, delayMillis = delayMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scale$ring"
        )
        val alpha by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, delayMillis = delayMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha$ring"
        )
        Box(
            Modifier
                .size(112.dp)
                .scale(scale)
                .background(color.copy(alpha = alpha), CircleShape)
        )
    }
}
