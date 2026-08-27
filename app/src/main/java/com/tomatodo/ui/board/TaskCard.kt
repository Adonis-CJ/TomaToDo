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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tomatodo.data.model.Subject
import com.tomatodo.data.model.Task
import com.tomatodo.data.model.TaskStatus
import com.tomatodo.ui.theme.AppMono
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

/** 任务状态对应的身份色（待办=灰墨 / 进行中=朱砂 / 完成=松绿） */
fun statusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.TODO -> Line
    TaskStatus.DOING -> Cinnabar
    TaskStatus.DONE -> PineGreen
}

/**
 * 三态勾选框（自绘 Canvas + 动效）：空心灰圆 / 朱砂环+内点 / 实心松绿+白勾描边。
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

        drawCircle(color = ringColor, radius = radius, center = center, style = Stroke(width = stroke))
        if (fillScale > 0.01f) drawCircle(color = PineGreen, radius = radius * fillScale, center = center)
        if (dotScale > 0.01f) drawCircle(color = Cinnabar, radius = radius * 0.38f * dotScale, center = center)
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
                segment, color = Color.White,
                style = Stroke(width = stroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * 任务卡片（清爽版）：单一时间戳 + 大内容 + 轻按钮，去除时间重复与视觉抢眼。
 * 顶行（科目色点 chip + 时间）/ 内容大段 / 底行（勾选 + 浅底「开始番茄」）。
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
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val contentColor by animateColorAsState(
        targetValue = if (done) InkMuted else Ink,
        animationSpec = tween(200),
        label = "taskContent"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (done) 0.58f else 1f,
        animationSpec = tween(200),
        label = "taskAlpha"
    )

    Card(
        onClick = { if (!readOnly) onEdit() },
        modifier = modifier.graphicsLayer {
            scaleX = bounce.value
            scaleY = bounce.value
            alpha = cardAlpha
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (task.status == TaskStatus.DOING && !done) Cinnabar.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            // 左缘状态色条（随卡片圆角被一起裁剪）
            Box(Modifier.width(4.dp).fillMaxHeight().background(statusColor(task.status)))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp)) {
                // 顶行：科目 chip + 右侧时间（等宽，唯一时间戳）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subject != null) SubjectDot(subject)
                    else Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatTime(task.startTime),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = AppMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))

                // 内容大段
                Text(
                    task.content,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(Modifier.height(14.dp))

                // 底行：勾选 + 开始番茄（浅底文字钮，暗合墨·纸克制）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!readOnly) {
                        StatusCheckbox(status = task.status) {
                            scope.launch {
                                bounce.snapTo(0.96f)
                                bounce.animateTo(1f, spring(dampingRatio = 0.45f))
                            }
                            onToggle()
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!readOnly && onStartPomodoro != null && !done) {
                        FilledTonalButton(
                            onClick = { onStartPomodoro() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Cinnabar.copy(alpha = 0.1f),
                                contentColor = Cinnabar
                            ),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("开始番茄", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

/** 科目：色点 + 名称（紧凑） */
@Composable
private fun SubjectDot(subject: Subject) {
    val color = Color(subject.color)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            subject.name,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
