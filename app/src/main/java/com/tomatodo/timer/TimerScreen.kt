package com.tomatodo.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomatodo.data.model.PomodoroType
import com.tomatodo.ui.theme.AppMono

@Composable
fun TimerScreen(viewModel: TimerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val needOverlay by viewModel.needOverlayPermission.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val progress = if (state.totalMillis > 0L) {
        state.remainingMillis.toFloat() / state.totalMillis.toFloat()
    } else {
        1f
    }

    // 悬浮窗权限引导（OPTIMIZATION 技术债 #6）
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

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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

        Spacer(Modifier.height(40.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { viewModel.reset() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("重置")
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = { if (state.isRunning) viewModel.pause() else viewModel.start() },
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
            Spacer(Modifier.width(16.dp))
            FilledTonalButton(onClick = { viewModel.skip() }) {
                Icon(Icons.Outlined.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("跳过")
            }
        }
    }
}
