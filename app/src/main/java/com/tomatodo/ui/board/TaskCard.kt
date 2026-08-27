package com.tomatodo.ui.board

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.tomatodo.data.model.Subject
import com.tomatodo.data.model.Task
import com.tomatodo.data.model.TaskStatus
import com.tomatodo.ui.theme.Cinnabar
import com.tomatodo.ui.theme.Ink
import com.tomatodo.ui.theme.InkMuted
import com.tomatodo.ui.theme.Line
import com.tomatodo.ui.theme.PineGreen
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(epoch: Long): String =
    Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(timeFormatter)

/** 任务状态对应的身份色（OPTIMIZATION-BOARD §2.1：待办=灰墨 / 进行中=朱砂 / 完成=松绿） */
fun statusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.TODO -> Line
    TaskStatus.DOING -> Cinnabar
    TaskStatus.DONE -> PineGreen
}

/**
 * 三态勾选框（自绘 Canvas + 动效 A1/A9）：
 * 待办=空心灰圆 / 进行中=朱砂环+内点 / 完成=实心松绿+白勾描边动画。
 */
@Composable
fun StatusCheckbox(
    status: TaskStatus,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    val checked = status == TaskStatus.DONE
    val doing = status == TaskStatus.DOING

    val ringColor by animateColorAsState(
        targetValue = when {
            checked -> PineGreen
            doing -> Cinnabar
            else -> InkMuted
        },
        animationSpec = tween(200),
        label = "checkboxRing"
    )
    val fillScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "checkboxFill"
    )
    val dotScale by animateFloatAsState(
        targetValue = if (doing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "checkboxDot"
    )
    // A1：白勾笔画描边动画
    val checkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(220),
        label = "checkStroke"
    )

    Canvas(
        modifier = modifier
            .size(22.dp)
            .clickable(enabled = enabled, onClick = onToggle)
    ) {
        val stroke = 2.dp.toPx()
        val radius = size.minDimension / 2f - stroke
        val center = Offset(size.width / 2f, size.height / 2f)

        // 圆环
        drawCircle(
            color = ringColor,
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )
        // 完成实心填充（弹入）
        if (fillScale > 0.01f) {
            drawCircle(color = PineGreen, radius = radius * fillScale, center = center)
        }
        // 进行中内点
        if (dotScale > 0.01f) {
            drawCircle(color = Cinnabar, radius = radius * 0.38f * dotScale, center = center)
        }
        // 白勾描边（画出来）
        if (checkProgress > 0.01f) {
            val checkPath = Path().apply {
                moveTo(center.x - radius * 0.5f, center.y + radius * 0.02f)
                lineTo(center.x - radius * 0.12f, center.y + radius * 0.38f)
                lineTo(center.x + radius * 0.55f, center.y - radius * 0.32f)
            }
            val measure = PathMeasure().apply { setPath(checkPath, false) }
            val segment = Path()
            measure.getSegment(0f, measure.length * checkProgress, segment, true)
            drawPath(
                segment,
                color = Color.White,
                style = Stroke(width = stroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * 三态任务卡片（OPTIMIZATION-BOARD §2.2）：
 * 左缘状态色条 + 科目 chip + 内容（完成划线/变灰）+ 三态勾选框 + 开始番茄。
 * 点击主体进入编辑；勾选框直达完成。
 */
@Composable
fun TaskCard(
    task: Task,
    subject: Subject?,
    readOnly: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onStartPomodoro: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val done = task.isCompleted
    val density = LocalDensity.current

    // A3：完成弹跳（轻微实体感）
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    fun bounceOnce() {
        scope.launch {
            bounce.snapTo(0.965f)
            bounce.animateTo(1f, spring(dampingRatio = 0.45f))
        }
    }

    val contentColor by animateColorAsState(
        targetValue = if (done) InkMuted else Ink,
        animationSpec = tween(200),
        label = "taskContent"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (done) 0.62f else 1f,
        animationSpec = tween(200),
        label = "taskAlpha"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            done -> MaterialTheme.colorScheme.outlineVariant
            task.status == TaskStatus.DOING -> Cinnabar
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(200),
        label = "taskBorder"
    )

    Card(
        onClick = { if (!readOnly) onEdit() },
        modifier = modifier.graphicsLayer {
            scaleX = bounce.value
            scaleY = bounce.value
            alpha = cardAlpha
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            width = if (task.status == TaskStatus.DOING && !done) 1.dp else 0.dp,
            color = borderColor
        )
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // 左缘状态色条
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor(task.status))
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subject != null) {
                        SubjectChip(subject)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatTime(task.startTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    task.content,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    color = contentColor,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!readOnly) {
                        StatusCheckbox(status = task.status) {
                            bounceOnce()
                            onToggle()
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        "${formatTime(task.startTime)} – ${formatTime(task.endTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    if (!readOnly && onStartPomodoro != null && !done) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Cinnabar.copy(alpha = 0.12f))
                                .clickable(onClick = onStartPomodoro),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = "开始番茄",
                                tint = Cinnabar,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (!readOnly) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(onClick = onEdit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectChip(subject: Subject) {
    val color = Color(subject.color)
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            subject.name,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
