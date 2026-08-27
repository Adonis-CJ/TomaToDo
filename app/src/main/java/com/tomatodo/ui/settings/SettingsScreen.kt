package com.tomatodo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomatodo.data.preferences.ThemeMode

private val ringtoneOptions = listOf(
    "default" to "默认",
    "gentle" to "轻柔",
    "crisp" to "清脆"
)

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    var showRingtoneMenu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        // 番茄钟
        Text("番茄钟", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        DurationSlider("专注时长", settings.focusMinutes, 5..120, viewModel::setFocus)
        DurationSlider("短休息", settings.shortBreakMinutes, 1..30, viewModel::setShort)
        DurationSlider("长休息", settings.longBreakMinutes, 5..60, viewModel::setLong)
        CountSlider("长休息前番茄数", settings.pomodorosBeforeLongBreak, 1..8, viewModel::setPomodoros)

        Spacer(Modifier.height(32.dp))

        // 提示音
        Text("提示音", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("铃声", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Box {
                OutlinedButton(onClick = { showRingtoneMenu = true }) {
                    Text(ringtoneOptions.find { it.first == settings.ringtoneId }?.second ?: "默认")
                }
                DropdownMenu(
                    expanded = showRingtoneMenu,
                    onDismissRequest = { showRingtoneMenu = false }
                ) {
                    ringtoneOptions.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.setRingtone(id)
                                showRingtoneMenu = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("音量", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(16.dp))
            Slider(
                value = settings.volume,
                onValueChange = viewModel::setVolume,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("静音 + 仅震动", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Switch(checked = settings.vibrationOnly, onCheckedChange = viewModel::setVibrationOnly)
        }

        Spacer(Modifier.height(32.dp))

        // 主题
        Text("主题", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = { Text(themeModeLabel(mode)) }
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DurationSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "$value 分钟",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1
        )
    }
}

@Composable
private fun CountSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "$value 个",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1
        )
    }
}
