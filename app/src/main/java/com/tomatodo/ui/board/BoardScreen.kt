@file:OptIn(ExperimentalMaterial3Api::class)

package com.tomatodo.ui.board

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomatodo.data.model.Subject
import com.tomatodo.data.model.Task
import com.tomatodo.data.model.TaskStatus
import com.tomatodo.ui.theme.Cinnabar
import com.tomatodo.ui.theme.PineGreen
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日")

private fun monthDay(date: LocalDate): String = date.format(monthDayFormatter)

/** 标题语义：今天 / 回顾 / 预排（OPTIMIZATION-BOARD §2.5） */
private fun titleFor(date: LocalDate, isToday: Boolean): String = when {
    isToday -> "今日看板"
    date < LocalDate.now() -> "${monthDay(date)} · 回顾"
    else -> "${monthDay(date)} · 预排"
}

/**
 * 看板（OPTIMIZATION-BOARD 全量改造）：
 * 日期导航（历史/未来任意日）+ 今日进度头部 + 三列三态卡片 + 滑动删除撤销 + 开始番茄。
 */
@Composable
fun BoardScreen(
    onStartPomodoro: (Task) -> Unit = {},
    viewModel: TaskViewModel = viewModel()
) {
    val state by viewModel.boardState.collectAsState()
    val subjects by viewModel.subjects.collectAsState(initial = emptyList())
    val selectedSubjectId by viewModel.selectedSubjectId.collectAsState()
    val subjectById = remember(subjects) { subjects.associateBy { it.id } }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    var sheetTask by remember { mutableStateOf<Task?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }

    fun handleDelete(task: Task) {
        viewModel.delete(task)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "已删除「${task.content.take(8)}${if (task.content.length > 8) "…" else ""}」",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(task)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 头部：标题 + 回到今天
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(titleFor(state.date, state.isToday), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        monthDay(state.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { sheetTask = null; showCreateSheet = true }) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("新建任务")
                }
            }

            // 日期导航条（← 日期 → + DatePicker + 回到今天）
            DateNavBar(
                date = state.date,
                isToday = state.isToday,
                onShift = viewModel::shiftDate,
                onPickDate = { showDatePicker = true },
                onBackToToday = viewModel::backToToday
            )

            // 科目筛选行（OPTIMIZATION 收尾）
            if (subjects.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                BoardSubjectFilter(
                    subjects = subjects,
                    selected = selectedSubjectId,
                    onSelect = viewModel::selectSubject
                )
            }

            // 进度头部（当日完成率，动画更新）
            ProgressHeader(done = state.doneCount, total = state.tasks.size)

            Spacer(Modifier.height(8.dp))

            // 三列（A11：切日 crossfade + 轻位移）
            AnimatedContent(
                targetState = state.date,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInHorizontally { it / 24 }) togetherWith fadeOut(tween(150))
                },
                label = "boardDate",
                modifier = Modifier.fillMaxSize()
            ) { _ ->
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BoardColumn(
                        title = "待办",
                        status = TaskStatus.TODO,
                        tasks = state.tasks,
                        subjectById = subjectById,
                        readOnly = !state.isToday,
                        onToggle = viewModel::toggleDone,
                        onEdit = { sheetTask = it; showCreateSheet = false },
                        onStart = { onStartPomodoro(it) },
                        onDelete = { handleDelete(it) },
                        modifier = Modifier.weight(1f)
                    )
                    BoardColumn(
                        title = "进行中",
                        status = TaskStatus.DOING,
                        tasks = state.tasks,
                        subjectById = subjectById,
                        readOnly = !state.isToday,
                        onToggle = viewModel::toggleDone,
                        onEdit = { sheetTask = it; showCreateSheet = false },
                        onStart = { onStartPomodoro(it) },
                        onDelete = { handleDelete(it) },
                        modifier = Modifier.weight(1f)
                    )
                    BoardColumn(
                        title = "已完成",
                        status = TaskStatus.DONE,
                        tasks = state.tasks,
                        subjectById = subjectById,
                        readOnly = !state.isToday,
                        onToggle = viewModel::toggleDone,
                        onEdit = { sheetTask = it; showCreateSheet = false },
                        onStart = null,
                        onDelete = { handleDelete(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.selectDate(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showCreateSheet || sheetTask != null) {
        TaskEditSheet(
            initial = sheetTask,
            subjects = subjects,
            onDismiss = { showCreateSheet = false; sheetTask = null },
            onSave = { content, start, end, subjectId, status ->
                val existing = sheetTask
                if (existing == null) {
                    viewModel.addTask(content, start, end, subjectId)
                } else {
                    viewModel.updateTask(
                        existing.copy(
                            content = content.trim(),
                            startTime = start,
                            endTime = end,
                            subjectId = subjectId,
                            status = status,
                            isCompleted = status == TaskStatus.DONE
                        )
                    )
                }
                showCreateSheet = false
                sheetTask = null
            }
        )
    }
}

/** 看板科目筛选行（与卡片页交互一致） */
@Composable
private fun BoardSubjectFilter(
    subjects: List<Subject>,
    selected: Long?,
    onSelect: (Long?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("全部") }
            )
        }
        items(subjects, key = { it.id }) { s ->
            FilterChip(
                selected = selected == s.id,
                onClick = { onSelect(if (selected == s.id) null else s.id) },
                label = { Text(s.name) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(s.color))
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(s.color),
                    selectedLabelColor = Color.White
                )
            )
        }
        item(key = "unassigned") {
            FilterChip(
                selected = selected == com.tomatodo.ui.cards.UNASSIGNED_SUBJECT_ID,
                onClick = {
                    onSelect(
                        if (selected == com.tomatodo.ui.cards.UNASSIGNED_SUBJECT_ID) null
                        else com.tomatodo.ui.cards.UNASSIGNED_SUBJECT_ID
                    )
                },
                label = { Text("未分类") }
            )
        }
    }
}

@Composable
private fun DateNavBar(
    date: LocalDate,
    isToday: Boolean,
    onShift: (Long) -> Unit,
    onPickDate: () -> Unit,
    onBackToToday: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onShift(-1L) }) {
            Icon(
                Icons.Outlined.KeyboardArrowLeft,
                contentDescription = "前一天",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            onClick = onPickDate,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isToday) "${monthDay(date)} · 今天" else monthDay(date),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Outlined.CalendarToday,
                    contentDescription = "选择日期",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = { onShift(1L) }) {
            Icon(
                Icons.Outlined.KeyboardArrowRight,
                contentDescription = "后一天",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        if (!isToday) {
            TextButton(onClick = onBackToToday) { Text("回到今天") }
        }
    }
}

@Composable
private fun ProgressHeader(done: Int, total: Int) {
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else done.toFloat() / total,
        animationSpec = tween(300),
        label = "dayProgress"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$done/$total 已完成",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(MaterialTheme.shapes.small),
            color = if (progress >= 1f && total > 0) PineGreen else Cinnabar,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun BoardColumn(
    title: String,
    status: TaskStatus,
    tasks: List<Task>,
    subjectById: Map<Long, Subject>,
    readOnly: Boolean,
    onToggle: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onStart: ((Task) -> Unit)?,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val columnTasks = tasks.filter { it.status == status }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 14.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(statusColor(status))
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    "(${columnTasks.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            if (columnTasks.isEmpty()) {
                EmptyColumnHint()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(columnTasks, key = { it.id }) { task ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    onDelete(task)
                                    true
                                } else {
                                    false
                                }
                            }
                        )
                        Box(Modifier.animateItem()) {
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Row(
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "删除",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 20.dp)
                                        )
                                    }
                                }
                            ) {
                                TaskCard(
                                    task = task,
                                    subject = subjectById[task.subjectId],
                                    readOnly = readOnly,
                                    onToggle = { onToggle(task) },
                                    onEdit = { onEdit(task) },
                                    onStartPomodoro = if (status == TaskStatus.DONE) null
                                        else onStart?.let { handler -> { handler(task) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyColumnHint() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.Inbox,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "暂无任务",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun TaskEditSheet(
    initial: Task?,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (content: String, startTime: Long, endTime: Long, subjectId: Long?, status: TaskStatus) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var selectedSubjectId by remember { mutableStateOf(initial?.subjectId) }
    var status by remember { mutableStateOf(initial?.status ?: TaskStatus.TODO) }
    val zone = ZoneId.systemDefault()
    val initialStart = initial?.startTime ?: System.currentTimeMillis()
    val initialEnd = initial?.endTime ?: (System.currentTimeMillis() + 25 * 60_000L)
    var startTime by remember { mutableStateOf(initialStart) }
    var endTime by remember { mutableStateOf(initialEnd) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(true) }
    var showSubjectMenu by remember { mutableStateOf(false) }
    // 结束时间：先选日期再选时分（跨天任务）
    var showEndDatePicker by remember { mutableStateOf(false) }
    var endDateMillis by remember { mutableStateOf<Long?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                if (initial == null) "新建任务" else "编辑任务",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("任务内容") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            TimeRow("开始时间", startTime, withDate = false) { pickingEnd = false; showTimePicker = true }
            Spacer(Modifier.height(4.dp))
            TimeRow("结束时间", endTime, withDate = true) { showEndDatePicker = true }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { showSubjectMenu = true }) {
                        Text(subjects.find { it.id == selectedSubjectId }?.name ?: "选择科目")
                    }
                    DropdownMenu(
                        expanded = showSubjectMenu,
                        onDismissRequest = { showSubjectMenu = false }
                    ) {
                        subjects.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                onClick = { selectedSubjectId = s.id; showSubjectMenu = false }
                            )
                        }
                    }
                }
                if (initial != null) {
                    FilterChip(
                        selected = status == TaskStatus.DOING,
                        onClick = { status = if (status == TaskStatus.DOING) TaskStatus.TODO else TaskStatus.DOING },
                        label = { Text("进行中") }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(content, startTime, endTime, selectedSubjectId, status) },
                    enabled = content.isNotBlank()
                ) { Text("保存") }
            }
        }
    }

    if (showTimePicker) {
        val zoneId = ZoneId.systemDefault()
        val initialEpoch = if (pickingEnd) endTime else startTime
        val zdt = Instant.ofEpochMilli(initialEpoch).atZone(zoneId)
        val timeState = rememberTimePickerState(
            initialHour = zdt.hour,
            initialMinute = zdt.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(if (pickingEnd) "选择结束时间" else "选择开始时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    // 结束时间：用 DatePicker 所选日期（若有）+ 此时分合成
                    val baseDate = if (pickingEnd && endDateMillis != null) {
                        Instant.ofEpochMilli(endDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                    } else {
                        Instant.ofEpochMilli(initialEpoch).atZone(zoneId).toLocalDate()
                    }
                    val result = baseDate
                        .atTime(timeState.hour, timeState.minute)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli()
                    if (pickingEnd) endTime = result else startTime = result
                    endDateMillis = null
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } }
        )
    }

    // 结束日期选择（DatePicker）：选完日期后转入 TimePicker
    if (showEndDatePicker) {
        val endLocalDate = Instant.ofEpochMilli(endTime).atZone(zone).toLocalDate()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endLocalDate
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val sel = datePickerState.selectedDateMillis
                    if (sel != null) {
                        endDateMillis = sel
                        // 选完日期接着选时分
                        showEndDatePicker = false
                        pickingEnd = true
                        showTimePicker = true
                    } else {
                        showEndDatePicker = false
                    }
                }) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState, showModeToggle = true)
        }
    }
}

@Composable
private fun TimeRow(label: String, epoch: Long, withDate: Boolean, onClick: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern(if (withDate) "M月d日 HH:mm" else "HH:mm")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(formatter),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
