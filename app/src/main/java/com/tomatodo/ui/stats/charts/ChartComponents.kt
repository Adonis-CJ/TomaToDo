package com.tomatodo.ui.stats.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 统计图表组件库（OPTIMIZATION §8.4）：Compose Canvas 自绘，零第三方依赖。
 */

/** V2：每日专注柱状图。今日高亮；点击柱子浮出数值。 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    barColor: Color,
    highlightIndex: Int,
    modifier: Modifier = Modifier,
    valueFormatter: (Double) -> String = { "${it.toInt()}m" }
) {
    var selected by remember { mutableIntStateOf(-1) }
    val enterProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(500),
        label = "barEnter"
    )
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(values.size) {
                    detectTapGestures { offset ->
                        if (values.isNotEmpty()) {
                            val w = size.width.toFloat()
                            selected = (offset.x / w * values.size)
                                .toInt()
                                .coerceIn(0, values.size - 1)
                        }
                    }
                }
        ) {
            if (values.isEmpty()) return@Canvas
            val maxV = values.max().coerceAtLeast(1.0)
            val slot = size.width / values.size
            val barWidth = slot * 0.62f
            values.forEachIndexed { i, v ->
                val h = (size.height * (v / maxV)).toFloat() * enterProgress
                val x = i * slot + (slot - barWidth) / 2f
                val isLit = i == selected || i == highlightIndex
                drawRoundRect(
                    color = if (isLit) barColor else barColor.copy(alpha = 0.38f),
                    topLeft = Offset(x, size.height - h),
                    size = Size(barWidth, h.coerceAtLeast(if (v > 0) 3f else 1.5f)),
                    cornerRadius = CornerRadius(barWidth / 3f)
                )
            }
        }
        // 选中数值提示
        if (selected >= 0 && selected < values.size) {
            Text(
                "${labels.getOrNull(selected) ?: ""} ${valueFormatter(values[selected])}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(2.dp)
            )
        }
    }
}

/** V3：科目分布环形图（含中心文字槽位与图例由调用方组合）。 */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    ringWidth: Float,
    modifier: Modifier = Modifier,
    centerContent: @Composable () -> Unit = {}
) {
    val sweepProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(600),
        label = "donutEnter"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val total = segments.sumOf { it.value }.coerceAtLeast(1.0)
            var start = -90f
            val inset = ringWidth
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            segments.forEach { seg ->
                val sweep = (seg.value / total * 360f).toFloat() * sweepProgress
                drawArc(
                    color = seg.color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = ringWidth, cap = StrokeCap.Butt)
                )
                start += (seg.value / total * 360f).toFloat()
            }
        }
        centerContent()
    }
}

data class DonutSegment(val value: Double, val color: Color)

/** V1：迷你趋势线（sparkline）。 */
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val maxV = values.max().coerceAtLeast(1.0)
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (size.height * 0.85f * (v / maxV)).toFloat() - size.height * 0.075f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

/** V4：12 周连续专注热力图（GitHub 打卡风格）。value: 0..1 归一化强度。 */
@Composable
fun HeatmapCalendar(
    weeklyValues: List<List<Double>>,
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        if (weeklyValues.isEmpty()) return@Canvas
        val cell = size.width / weeklyValues.size
        val gap = cell * 0.22f
        val draw = cell - gap
        weeklyValues.forEachIndexed { w, week ->
            week.forEachIndexed { d, v ->
                val alpha = when {
                    v <= 0.0 -> 0f
                    v < 0.25 -> 0.25f
                    v < 0.5 -> 0.45f
                    v < 0.75 -> 0.7f
                    else -> 1f
                }
                drawRoundRect(
                    color = if (alpha == 0f) baseColor.copy(alpha = 0.08f)
                    else baseColor.copy(alpha = alpha),
                    topLeft = Offset(w * cell, d * cell),
                    size = Size(draw, draw),
                    cornerRadius = CornerRadius(draw * 0.25f)
                )
            }
        }
    }
}
