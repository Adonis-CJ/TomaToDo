@file:OptIn(ExperimentalMaterial3Api::class)

package com.tomatodo.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import com.tomatodo.data.model.Task
import com.tomatodo.data.model.TaskStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("M月d日")

private fun formatTime(epoch: Long): String =
    Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(timeFormatter)

private fun formatDate(epoch: Long): String =
    Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(dateFormatter)

@Composable
fun BoardScreen(viewModel: TaskViewModel = viewModel()) {
    val tasks by viewModel.tasks.collectAsState(initial = emptyList())
    val subjects by viewModel.subjects.collectAsState(initial = emptyList())
    var showAddSheet by remember { mutableStateOf(false) }

    val subjectById = remember(subjects) { subjects.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("今日看板", style = MaterialTheme.typography.headlineMedium)
                Text(
                    formatDate(System.currentTimeMillis()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { showAddSheet = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建任务")
            }
        }

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TaskColumn("待办", TaskStatus.TODO, tasks, subjectById, viewModel, Modifier.weight(1f))
            TaskColumn("进行中", TaskStatus.DOING, tasks, subjectById, viewModel, Modifier.weight(1f))
            TaskColumn("已完成", TaskStatus.DONE, tasks, subjectById, viewModel, Modifier.weight(1f))
        }
    }

    if (showAddSheet) {
        AddTaskSheet(
            subjects = subjects,
            onDismiss = { showAddSheet = false },
            onSave = { content, start, end, subjectId ->
                viewModel.addTask(content, start, end, subjectId)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun TaskColumn(
    title: String,
    status: TaskStatus,
    allTasks: List<Task>,
    subjectById: Map<Long, Subject>,
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val columnTasks = allTasks.filter { it.status == status }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    "(${columnTasks.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(columnTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        subject = subjectById[task.subjectId],
                        onClick = { viewModel.advanceStatus(task) },
                        onDelete = { viewModel.delete(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    subject: Subject?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val subjectColor = subject?.let { Color(it.color) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subject != null && subjectColor != null) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(subjectColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        subject.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onDelete() },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(task.content, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatTime(task.startTime)} – ${formatTime(task.endTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddTaskSheet(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (content: String, startTime: Long, endTime: Long, subjectId: Long?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var content by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }
    val startTime = remember { System.currentTimeMillis() }
    var endTime by remember { mutableStateOf(startTime + 25 * 60_000L) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showSubjectMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("新建任务", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("任务内容") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("开始时间", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    formatTime(startTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showTimePicker = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("结束时间", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    formatTime(endTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))

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
                            onClick = {
                                selectedSubjectId = s.id
                                showSubjectMenu = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(content, startTime, endTime, selectedSubjectId) }) {
                    Text("保存")
                }
            }
        }
    }

    if (showTimePicker) {
        EndTimePickerDialog(
            initialTime = endTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { endTime = it; showTimePicker = false }
        )
    }
}

@Composable
private fun EndTimePickerDialog(
    initialTime: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val initial = remember(initialTime) {
        val zdt = Instant.ofEpochMilli(initialTime).atZone(zone)
        zdt.hour to zdt.minute
    }
    val timeState = rememberTimePickerState(
        initialHour = initial.first,
        initialMinute = initial.second,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择结束时间") },
        text = { TimePicker(state = timeState) },
        confirmButton = {
            TextButton(onClick = {
                val zdt = Instant.ofEpochMilli(initialTime).atZone(zone)
                val result = zdt.toLocalDate()
                    .atTime(timeState.hour, timeState.minute)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                onConfirm(result)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
