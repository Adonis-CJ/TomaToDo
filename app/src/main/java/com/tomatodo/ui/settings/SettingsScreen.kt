package com.tomatodo.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomatodo.data.model.Subject
import com.tomatodo.data.preferences.ThemeMode

private val ringtoneOptions = listOf(
    "default" to "默认",
    "gentle" to "轻柔",
    "crisp" to "清脆"
)

private val subjectColorPresets = listOf(
    0xFF3F6B5F, 0xFFB05C42, 0xFFA8893C, 0xFF6E7A3F, 0xFF7A5C45,
    0xFF8A857C, 0xFFB08D6A, 0xFF9C5A3C, 0xFF4E6B5A, 0xFF8B6B4A
)

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    var showRingtoneMenu by remember { mutableStateOf(false) }
    var showAddSubject by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportTo(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFrom(it) } }

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

        // 科目管理
        Text("科目管理", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        subjects.forEach { subject ->
            SubjectRow(
                subject = subject,
                onDelete = { viewModel.deleteSubject(subject) }
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showAddSubject = true }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加科目")
        }

        Spacer(Modifier.height(32.dp))

        // 数据备份
        Text("数据备份", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("tomatodo_backup.json") }) {
                Text("导出备份")
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                Text("导入备份")
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showAddSubject) {
        AddSubjectDialog(
            onDismiss = { showAddSubject = false },
            onSave = { name, color ->
                viewModel.addSubject(name, color)
                showAddSubject = false
            }
        )
    }
}

@Composable
private fun SubjectRow(subject: Subject, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(subject.color))
        )
        Spacer(Modifier.width(12.dp))
        Text(subject.name, style = MaterialTheme.typography.bodyLarge)
        if (subject.isBuiltIn) {
            Spacer(Modifier.width(8.dp))
            Text(
                "内置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        if (!subject.isBuiltIn) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(subjectColorPresets.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加科目") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("科目名称") },
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Text("颜色", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjectColorPresets.forEach { c ->
                        val selected = selectedColor == c
                        Box(
                            Modifier
                                .size(if (selected) 32.dp else 28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { selectedColor = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, selectedColor) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
