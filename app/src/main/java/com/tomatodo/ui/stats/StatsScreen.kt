package com.tomatodo.ui.stats

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomatodo.ui.stats.charts.BarChart
import com.tomatodo.ui.stats.charts.DonutChart
import com.tomatodo.ui.stats.charts.DonutSegment
import com.tomatodo.ui.stats.charts.HeatmapCalendar
import com.tomatodo.ui.stats.charts.Sparkline
import com.tomatodo.ui.theme.Cinnabar
import com.tomatodo.ui.theme.PineGreen

private fun formatMinutes(minutes: Long): String =
    if (minutes >= 60) "${minutes / 60}h${if (minutes % 60 > 0) " ${minutes % 60}m" else ""}"
    else "${minutes}m"

/**
 * 学习统计（OPTIMIZATION §8）：指标 + 柱状图 + 科目环形图 + 12 周热力图 + 完成率 + CSV/周报。
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel()) {
    val stats by viewModel.stats.collectAsState()
    val context = LocalContext.current

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    val report = remember(stats) { buildWeeklyReport(stats) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("学习统计", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                StatsRange.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = stats.range == r,
                        onClick = { viewModel.setRange(r) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = StatsRange.entries.size)
                    ) { Text(r.label) }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // V1：专注时长大数字 + 迷你趋势
            StatCard(Modifier.weight(1f)) {
                Text(
                    "${stats.range.label}专注",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    formatMinutes(stats.rangeFocusMinutes),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(8.dp))
                Sparkline(
                    values = stats.dailyFocus.takeLast(7).map { it.minutes.toDouble() },
                    color = Cinnabar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                )
            }
            // V3：科目分布环形图
            StatCard(Modifier.weight(1.2f)) {
                Text(
                    "科目分布",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (stats.subjectShares.isEmpty()) {
                    EmptyHint("暂无专注记录")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(
                            segments = stats.subjectShares.map {
                                DonutSegment(
                                    it.minutes.toDouble(),
                                    it.subject?.let { s -> Color(s.color) } ?: MaterialTheme.colorScheme.outline
                                )
                            },
                            ringWidth = 16.dp.value,
                            modifier = Modifier.size(110.dp)
                        ) {
                            Text(
                                formatMinutes(stats.subjectShares.sumOf { it.minutes }),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            stats.subjectShares.take(4).forEach { share ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                share.subject?.let { Color(it.color) }
                                                    ?: MaterialTheme.colorScheme.outline
                                            )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${share.subject?.name ?: "未分类"} ${(share.fraction * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // V2：近 30 日柱状图（今日高亮）
        StatCard(Modifier.fillMaxWidth()) {
            Text(
                "近 30 日专注",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            BarChart(
                values = stats.dailyFocus.map { it.minutes.toDouble() },
                labels = stats.dailyFocus.map { "${it.date.monthValue}/${it.date.dayOfMonth}" },
                barColor = Cinnabar,
                highlightIndex = stats.dailyFocus.indexOfLast { it.date == java.time.LocalDate.now() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                valueFormatter = { formatMinutes(it.toLong()) }
            )
        }
        Spacer(Modifier.height(16.dp))

        // V4：12 周热力图 + 连续专注
        StatCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "连续专注 ${stats.streakDays} 天",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "今日 ${formatMinutes(stats.todayMinutes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            HeatmapCalendar(
                weeklyValues = stats.heatmapWeeks,
                baseColor = PineGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        // V5：按科目完成率
        StatCard(Modifier.fillMaxWidth()) {
            Text(
                "任务完成率",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            if (stats.completionBySubject.isEmpty()) {
                EmptyHint("暂无关联科目的任务")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.completionBySubject.take(6).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        item.subject?.let { Color(it.color) }
                                            ?: MaterialTheme.colorScheme.outline
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                item.subject?.name ?: "未分类",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(96.dp),
                                maxLines = 1
                            )
                            LinearProgressIndicator(
                                progress = {
                                    if (item.total == 0) 0f
                                    else item.done.toFloat() / item.total
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(MaterialTheme.shapes.small),
                                color = item.subject?.let { Color(it.color) }
                                    ?: MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${item.done}/${item.total}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // 导出与周报
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { csvLauncher.launch("toma_todo_stats.csv") }) {
                Text("导出 CSV")
            }
            Button(onClick = {
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                            },
                            "分享学习周报"
                        )
                    )
                }
            }) { Text("分享周报") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/** 周报文本（OPTIMIZATION §8.4） */
private fun buildWeeklyReport(s: StatsUiState): String = buildString {
    appendLine("【TomaTodo 学习统计】")
    appendLine("本${s.range.label}专注 ${formatMinutes(s.rangeFocusMinutes)}，累计 ${s.totalPomodoros} 个番茄。")
    appendLine("今日专注 ${formatMinutes(s.todayMinutes)}，连续专注 ${s.streakDays} 天。")
    if (s.subjectShares.isNotEmpty()) {
        appendLine("科目分布：")
        s.subjectShares.take(4).forEach {
            appendLine(" · ${it.subject?.name ?: "未分类"}：${formatMinutes(it.minutes)}（${(it.fraction * 100).toInt()}%）")
        }
    }
}
