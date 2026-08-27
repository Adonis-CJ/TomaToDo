package com.tomatodo.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomatodo.data.model.Subject
import com.tomatodo.data.preferences.ThemeMode
import com.tomatodo.ui.theme.AppMono

private val ringtoneOptions = listOf(
    "default" to "默认",
    "gentle" to "轻柔",
    "crisp" to "清脆"
)

private val subjectColorPresets = listOf(
    0xFF3F6B5F, 0xFFB05C42, 0xFFA8893C, 0xFF6E7A3F, 0xFF7A5C45,
    0xFF8A857C, 0xFFB08D6A, 0xFF9C5A3C, 0xFF4E6B5A, 0xFF8B6B4A
)

private fun ringtoneLabel(id: String): String =
    ringtoneOptions.find { it.first == id }?.second ?: "默认"

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

/** 设置页（OPTIMIZATION §3.4）：分组卡片化 + 统一行高与控件。 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    var showRingtoneMenu by remember { mutableStateOf(false) }
    var showAddSubject by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.exportTo(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFrom(it) } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val pickWallpaper = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.setWallpaper(uri) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        // ---- 番茄钟 ----
        SettingsGroup(title = "番茄钟", icon = Icons.Outlined.Timer) {
            StepperRow(
                label = "专注时长",
                value = settings.focusMinutes,
                range = 5..120,
                suffix = "分钟"
            ) { viewModel.setFocus(it) }
            StepperRow(
                label = "短休息",
                value = settings.shortBreakMinutes,
                range = 1..30,
                suffix = "分钟"
            ) { viewModel.setShort(it) }
            StepperRow(
                label = "长休息",
                value = settings.longBreakMinutes,
                range = 5..60,
                suffix = "分钟"
            ) { viewModel.setLong(it) }
            StepperRow(
                label = "长休息前番茄数",
                value = settings.pomodorosBeforeLongBreak,
                range = 1..8,
                suffix = "个"
            ) { viewModel.setPomodoros(it) }
        }
        Spacer(Modifier.height(16.dp))

        // ---- 沉浸模式 ----
        SettingsGroup(title = "沉浸模式", icon = Icons.Outlined.Fullscreen) {
            Text(
                "开始计时 3 秒后自动进入全屏沉浸；轻触屏幕呼出控制",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ImmersionPreviewCard(
                    title = "翻页钟",
                    selected = settings.immersionMode == com.tomatodo.data.preferences.ImmersionMode.FLIP,
                    onSelect = { viewModel.setImmersionMode(com.tomatodo.data.preferences.ImmersionMode.FLIP) },
                    modifier = Modifier.weight(1f)
                ) {
                    // 迷你预览：4 张翻页卡（与真实翻页钟一致的分离黑卡）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF050506))
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniFlipCard("2")
                        MiniFlipCard("5")
                        MiniFlipColon()
                        MiniFlipCard("0")
                        MiniFlipCard("0")
                    }
                }
                ImmersionPreviewCard(
                    title = "背景图时钟",
                    selected = settings.immersionMode == com.tomatodo.data.preferences.ImmersionMode.PHOTO,
                    onSelect = { viewModel.setImmersionMode(com.tomatodo.data.preferences.ImmersionMode.PHOTO) },
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B2A26)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (settings.wallpaperPath != null) {
                            coil.compose.AsyncImage(
                                model = java.io.File(context.filesDir, settings.wallpaperPath),
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(96.dp)
                            )
                            // 中央时钟遮罩
                            Text(
                                "25:00",
                                fontFamily = AppMono,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            Text(
                                "25:00",
                                fontFamily = AppMono,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 36.dp)
                            )
                        }
                    }
                }
            }
            if (settings.immersionMode == com.tomatodo.data.preferences.ImmersionMode.PHOTO) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        pickWallpaper.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (settings.wallpaperPath == null) "上传背景图" else "更换背景图") }
                if (settings.wallpaperPath != null) {
                    TextButton(onClick = { viewModel.setWallpaper(null) }) { Text("恢复默认深色背景") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---- 提示音 ----
        SettingsGroup(title = "提示音", icon = Icons.Outlined.VolumeUp) {
            MenuRow(
                label = "铃声",
                value = ringtoneLabel(settings.ringtoneId),
                expanded = showRingtoneMenu,
                onToggleMenu = { showRingtoneMenu = !showRingtoneMenu },
                onDismiss = { showRingtoneMenu = false },
                options = ringtoneOptions,
                onSelect = {
                    viewModel.setRingtone(it)
                    showRingtoneMenu = false
                }
            )
            StepperRow(
                label = "音量",
                value = (settings.volume * 100).toInt(),
                range = 0..100,
                step = 10,
                format = { "$it%" }
            ) { viewModel.setVolume(it / 100f) }
            SwitchRow(
                title = "静音 + 仅震动",
                description = "图书馆安静场合使用",
                checked = settings.vibrationOnly,
                onCheckedChange = viewModel::setVibrationOnly
            )
        }
        Spacer(Modifier.height(16.dp))

        // ---- 外观 ----
        SettingsGroup(title = "外观", icon = Icons.Outlined.DarkMode) {
            ThemePreviewSelector(
                selected = settings.themeMode,
                onSelect = viewModel::setThemeMode
            )
        }
        Spacer(Modifier.height(16.dp))

        // ---- 科目管理 ----
        SettingsGroup(title = "科目管理", icon = Icons.Outlined.Label) {
            subjects.forEach { subject ->
                SubjectRow(
                    subject = subject,
                    onDelete = { viewModel.deleteSubject(subject) }
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showAddSubject = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加科目")
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---- 数据备份 ----
        SettingsGroup(title = "数据备份", icon = Icons.Outlined.Archive) {
            Text(
                "导出为 ZIP 包（含卡片图片）；导入自动识别 ZIP / 旧版 JSON",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("tomatodo_backup.zip") },
                    modifier = Modifier.weight(1f)
                ) { Text("导出备份") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("导入备份") }
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

// ---- 分组卡片容器 ----

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

// ---- 统一行组件 ----

/** 步进器行（OPTIMIZATION UI 2.0）：圆形 ± 按钮 + 等宽数值，精准且不误触。 */
@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    step: Int = 1,
    format: (Int) -> String = { if (suffix.isEmpty()) "$it" else "$it $suffix" },
    onChange: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        StepperButton(
            icon = Icons.Outlined.Remove,
            enabled = value - step >= range.first
        ) { onChange((value - step).coerceIn(range.first, range.last)) }
        Box(Modifier.width(88.dp), contentAlignment = Alignment.Center) {
            Text(
                format(value),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = AppMono,
                color = MaterialTheme.colorScheme.primary
            )
        }
        StepperButton(
            icon = Icons.Outlined.Add,
            enabled = value + step <= range.last
        ) { onChange((value + step).coerceIn(range.first, range.last)) }
    }
}

@Composable
private fun StepperButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MenuRow(
    label: String,
    value: String,
    expanded: Boolean,
    onToggleMenu: () -> Unit,
    onDismiss: () -> Unit,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleMenu) { Text(value) }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(id) }
                )
            }
        }
    }
}

/** 主题三选：迷你预览卡（浅色 / 深色 / 跟随系统），选中朱砂描边。 */
@Composable
private fun ThemePreviewSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = selected == mode
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(mode) }
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(3.dp)
                ) {
                    ThemeMiniPreview(mode)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    themeModeLabel(mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 迷你主题预览：模拟一张卡片 + 一条任务 + 朱砂点缀 */
@Composable
private fun ThemeMiniPreview(mode: ThemeMode) {
    val bg = when (mode) {
        ThemeMode.LIGHT -> Color(0xFFF6F3EC)
        ThemeMode.DARK -> Color(0xFF1E1C19)
        ThemeMode.SYSTEM -> Color(0xFFEDE8DD)
    }
    val card = when (mode) {
        ThemeMode.LIGHT -> Color(0xFFFDFCF8)
        ThemeMode.DARK -> Color(0xFF262421)
        ThemeMode.SYSTEM -> Color(0xFFFDFCF8)
    }
    val line = when (mode) {
        ThemeMode.LIGHT -> Color(0xFF2B2A26)
        ThemeMode.DARK -> Color(0xFFEDE8DD)
        ThemeMode.SYSTEM -> Color(0xFF2B2A26)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(8.dp)
    ) {
        Row {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB4553A))
            )
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(line.copy(alpha = 0.55f))
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(card)
                .padding(6.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(line.copy(alpha = 0.35f))
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(line.copy(alpha = 0.2f))
            )
        }
    }
}

/** 沉浸模式预览卡：迷你效果示意 + 选中描边 */
@Composable
private fun ImmersionPreviewCard(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier.clickable(onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(10.dp)
                )
        ) { content() }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MiniFlipCard(text: String) {
    Box(
        Modifier
            .width(34.dp)
            .height(64.dp)
            .background(Color(0xFF161618), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF2A2A2E), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color(0xFFF5F2EC),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = AppMono
        )
    }
}

@Composable
private fun MiniFlipColon() {
    Column(
        Modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(Color(0xFF4A4A50), CircleShape)
        )
        Box(Modifier.height(12.dp))
        Box(
            Modifier
                .size(6.dp)
                .background(Color(0xFF4A4A50), CircleShape)
        )
    }
}

@Composable
private fun SubjectRow(subject: Subject, onDelete: () -> Unit) {    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp),
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
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
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
                Spacer(Modifier.height(10.dp))
                // 5 列色块网格，选中带描边环
                subjectColorPresets.chunked(5).forEach { rowColors ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowColors.forEach { c ->
                            val isSelected = selectedColor == c
                            Box(
                                Modifier
                                    .size(if (isSelected) 36.dp else 30.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .clickable { selectedColor = c }
                                    .then(
                                        if (isSelected) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        ) else Modifier
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedColor) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
