package com.tomatodo.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tomatodo.data.model.PomodoroType
import com.tomatodo.data.preferences.ImmersionMode
import com.tomatodo.data.preferences.TimerSettings
import com.tomatodo.ui.theme.AppMono
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun TimerScreen(
    viewModel: TimerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val needOverlay by viewModel.needOverlayPermission.collectAsState()
    val immersive by viewModel.isImmersive.collectAsState()
    val context = LocalContext.current

    var showControls by remember { mutableStateOf(false) }
    val progress = if (state.totalMillis > 0L) {
        state.remainingMillis.toFloat() / state.totalMillis.toFloat()
    } else {
        1f
    }

    // 计时开始 3s 后自动进入沉浸式（用户需求）；暂停/停止自动退出
    LaunchedEffect(state.isRunning) {
        if (state.isRunning) {
            delay(3000)
            if (state.isRunning) viewModel.enterImmersive()
        } else {
            viewModel.exitImmersive()
        }
    }

    // 沉浸式中控制层 3s 无操作自动隐藏
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    // 悬浮窗权限引导
    if (needOverlay) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.needOverlayPermission.value = false },
            title = { Text("开启悬浮窗？") },
            text = { Text("开启「显示在其他应用上层」权限后，可以在刷题、看笔记时通过小浮窗查看和控制番茄钟。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.needOverlayPermission.value = false
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("去开启") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.needOverlayPermission.value = false }
                ) { Text("暂不") }
            }
        )
    }

    if (immersive) {
        ImmersiveScreen(
            mode = settings.immersionMode,
            wallpaper = settings.wallpaperPath?.let { File(context.filesDir, it) },
            state = state,
            showControls = showControls,
            onToggleControls = { showControls = !showControls },
            onToggleRun = { if (state.isRunning) viewModel.pause() else viewModel.start() },
            onSkip = viewModel::skip,
            onStop = { viewModel.reset() },
            onExit = { viewModel.exitImmersive() }
        )
    } else {
        NormalTimerContent(
            state = state,
            progress = progress,
            tasks = viewModel.activeTasks.collectAsState(initial = emptyList()).value,
            onBindTask = viewModel::bindTask,
            immersionMode = settings.immersionMode,
            onSetImmersion = viewModel::setImmersionMode,
            onStart = viewModel::start,
            onPause = viewModel::pause,
            onReset = viewModel::reset,
            onSkip = viewModel::skip,
            onImmersive = { viewModel.enterImmersive() }
        )
    }
}

// ---------------- 正常模式 ----------------

@Composable
private fun NormalTimerContent(
    state: TimerController.TimerState,
    progress: Float,
    tasks: List<com.tomatodo.data.model.Task>,
    onBindTask: (Long?) -> Unit,
    immersionMode: ImmersionMode,
    onSetImmersion: (ImmersionMode) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
    onImmersive: () -> Unit
) {
    var showTaskMenu by remember { mutableStateOf(false) }
    val currentTask = tasks.find { it.id == state.taskId }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 当前任务选择器
        Box {
            androidx.compose.material3.FilledTonalButton(onClick = { showTaskMenu = true }) {
                Icon(
                    Icons.Outlined.TaskAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    currentTask?.content?.take(14) ?: "选择任务",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
            androidx.compose.material3.DropdownMenu(
                expanded = showTaskMenu,
                onDismissRequest = { showTaskMenu = false }
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("不关联任务") },
                    onClick = {
                        onBindTask(null)
                        showTaskMenu = false
                    }
                )
                tasks.filter { !it.isCompleted }.forEach { task ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(task.content, maxLines = 1) },
                        onClick = {
                            onBindTask(task.id)
                            showTaskMenu = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            phaseLabel(state.phase),
            style = MaterialTheme.typography.headlineMedium,
            color = if (state.phase == PomodoroType.FOCUS) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            }
        )
        Spacer(Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(300.dp),
                strokeWidth = 8.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatCountdown(state.remainingMillis),
                    fontSize = 64.sp,
                    fontFamily = AppMono,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "已完成 ${state.completedPomodoros} 个番茄",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        // 沉浸模式切换 + 入口（番茄页内可直接选翻页钟 / 背景图时钟）
        Row(
            Modifier.height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ImmersionModeChip(
                selected = immersionMode == ImmersionMode.FLIP,
                label = "翻页钟",
                onClick = { onSetImmersion(ImmersionMode.FLIP) }
            )
            ImmersionModeChip(
                selected = immersionMode == ImmersionMode.PHOTO,
                label = "背景图",
                onClick = { onSetImmersion(ImmersionMode.PHOTO) }
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onImmersive,
            enabled = state.isRunning,
            modifier = Modifier.height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.Outlined.CloseFullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("进入沉浸模式")
        }
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onReset) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("重置")
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = { if (state.isRunning) onPause() else onStart() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(
                    if (state.isRunning) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (state.isRunning) "暂停" else "开始", style = MaterialTheme.typography.titleMedium)
            }
            // 跳过仅允许在休息阶段（用户反馈：专注不可跳过）
            if (state.phase != PomodoroType.FOCUS) {
                Spacer(Modifier.width(16.dp))
                FilledTonalButton(onClick = onSkip) {
                    Icon(Icons.Outlined.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("跳过")
                }
            }
        }
    }
}

// ---------------- 沉浸式全屏 ----------------

@Composable
private fun ImmersiveScreen(
    mode: ImmersionMode,
    wallpaper: File?,
    state: TimerController.TimerState,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onToggleRun: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleControls
            )
    ) {
        when (mode) {
            ImmersionMode.FLIP -> FlipImmersive(state)
            ImmersionMode.PHOTO -> PhotoImmersive(state, wallpaper)
        }

        // 控制层（半透明，自动隐藏）
        if (showControls) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlIcon(Icons.Outlined.CloseFullscreen, "退出沉浸") { onExit() }
                ControlIcon(
                    if (state.isRunning) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    if (state.isRunning) "暂停" else "继续",
                    tint = Color.White,
                    onClick = onToggleRun
                )
                // 跳过仅休息阶段可用（专注不可跳过）
                if (state.phase != PomodoroType.FOCUS) {
                    ControlIcon(Icons.Outlined.SkipNext, "跳过休息") { onSkip() }
                }
                ControlIcon(Icons.Outlined.Refresh, "结束计时") { onStop() }
            }
        }

        // 操作提示（首次进入几秒可见，简化：常驻小字，控制层出现时隐藏）
        if (!showControls) {
            Text(
                "轻触屏幕显示控制",
                color = Color.White.copy(alpha = 0.25f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }
    }
}

/** 模式 A：黑底翻页钟 */
@Composable
private fun FlipImmersive(state: TimerController.TimerState) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            phaseLabel(state.phase),
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 36.dp)
        )
        FlipClock(formatCountdown(state.remainingMillis))
        Spacer(Modifier.height(40.dp))
        PomodoroDots(state.completedPomodoros)
    }
}

/** 模式 B：自定义背景图 + 中心时钟倒计时 */
@Composable
private fun PhotoImmersive(state: TimerController.TimerState, wallpaper: File?) {
    Box(Modifier.fillMaxSize()) {
        if (wallpaper != null && wallpaper.exists()) {
            AsyncImage(
                model = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // 压暗遮罩，保证文字可读
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                phaseLabel(state.phase),
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(28.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = {
                        if (state.totalMillis > 0L) {
                            state.remainingMillis.toFloat() / state.totalMillis.toFloat()
                        } else 1f
                    },
                    modifier = Modifier.size(260.dp),
                    strokeWidth = 6.dp,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Text(
                    formatCountdown(state.remainingMillis),
                    fontSize = 56.sp,
                    fontFamily = AppMono,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(28.dp))
            PomodoroDots(state.completedPomodoros)
        }
    }
}

/** 已完成番茄圆点（白/朱砂） */
@Composable
private fun PomodoroDots(completed: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(4) { i ->
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        if (i < completed) Color(0xFFB4553A) else Color.White.copy(alpha = 0.2f),
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun ImmersionModeChip(selected: Boolean, label: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = when {
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surface
        },
        border = if (selected) null
        else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = Color.White.copy(alpha = 0.85f),
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(26.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            contentDescription,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
