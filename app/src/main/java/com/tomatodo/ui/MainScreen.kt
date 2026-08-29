package com.tomatodo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.tomatodo.ui.board.BoardScreen
import com.tomatodo.ui.cards.CardDetailScreen
import com.tomatodo.ui.cards.CardsScreen
import com.tomatodo.ui.cards.TrashScreen
import com.tomatodo.ui.review.ReviewScreen
import com.tomatodo.ui.settings.SettingsScreen
import com.tomatodo.ui.stats.StatsScreen
import com.tomatodo.ui.theme.Motion
import com.tomatodo.timer.TimerScreen

/** 主导航目的地（图标为 Material Symbols 线性 SVG，非 emoji） */
enum class Destination(val label: String, val icon: ImageVector) {
    Board("看板", Icons.Outlined.Dashboard),
    Timer("番茄", Icons.Outlined.Timer),
    Review("复习", Icons.Outlined.CalendarMonth),
    Cards("卡片", Icons.Outlined.Style),
    Stats("统计", Icons.Outlined.BarChart),
    Settings("设置", Icons.Outlined.Settings),
}

/** 侧边栏顶部：考研倒计时 */
@Composable
private fun CountdownHeader() {
    val days = daysToExam()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    ) {
        Text(
            "距考研",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "$days",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = MaterialTheme.colorScheme.primary
            ),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Text(
            "天",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MainScreen() {
    var selected by remember { mutableStateOf(Destination.Board) }
    // 轻量路由：卡片详情覆盖层（viewerCardId 为 null 表示新建）
    var viewerOpen by remember { mutableStateOf(false) }
    var viewerCardId by remember { mutableStateOf<Long?>(null) }
    var trashOpen by remember { mutableStateOf(false) }
    // 番茄沉浸式全屏：隐藏导航栏（外壳根据此状态只渲染计时页）
    var timerImmersive by remember { mutableStateOf(false) }
    // 番茄钟 ViewModel 提升至 MainScreen，看板可一键启动
    val timerViewModel: com.tomatodo.timer.TimerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    // 番茄钟阶段完成全屏提醒（KMS v1.2 同期改进：结束更醒目）
    PhaseCompletionOverlay(
        timerViewModel = timerViewModel,
        visible = !viewerOpen && !trashOpen
    )

    AnimatedContent(
        targetState = Triple(viewerOpen, trashOpen, timerImmersive),
        transitionSpec = {
            // 覆盖层（卡片详情/回收站）方向性转场：自右滑入，退出反向；其余（沉浸切换）淡入淡出
            val enteringOverlay = targetState.first || targetState.second
            val leavingOverlay = initialState.first || initialState.second
            when {
                enteringOverlay && !leavingOverlay ->
                    (fadeIn(Motion.enter()) + slideInHorizontally(Motion.enter()) { it / 16 }) togetherWith
                        fadeOut(Motion.exit())
                !enteringOverlay && leavingOverlay ->
                    fadeIn(Motion.enter()) togetherWith
                        (fadeOut(Motion.exit()) + slideOutHorizontally(Motion.exit()) { it / 16 })
                else -> fadeIn(Motion.standard()) togetherWith fadeOut(Motion.exit())
            }
        },
        label = "overlay",
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) { (inViewer, inTrash, immersive) ->
        when {
            inViewer -> CardDetailScreen(
                cardId = viewerCardId,
                onBack = { viewerOpen = false }
            )
            inTrash -> TrashScreen(onBack = { trashOpen = false })
            else -> Row(Modifier.fillMaxSize()) {
                // 沉浸式时隐藏导航栏，实现真·全屏（不重建计时页状态）
                androidx.compose.animation.AnimatedVisibility(visible = !immersive) {
                    Column {
                        NavigationRail {
                            // 顶部：考研倒计时
                            CountdownHeader()
                            Spacer(Modifier.weight(1f))
                            Destination.entries.forEach { dest ->
                                NavigationRailItem(
                                    selected = selected == dest,
                                    onClick = { selected = dest },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label) }
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (!immersive) VerticalDivider()
                // 内容区（目的地切换转场：fade + 自下浮入）
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        (fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { it / 24 }) togetherWith
                            fadeOut(Motion.exit())
                    },
                    label = "destination",
                    modifier = Modifier.weight(1f)
                ) { dest ->
                    when (dest) {
                        Destination.Board -> BoardScreen(
                            onStartPomodoro = { task ->
                                timerViewModel.startForTask(task.id)
                                selected = Destination.Timer
                            }
                        )
                        Destination.Timer -> TimerScreen(
                            onImmersiveChanged = { timerImmersive = it },
                            viewModel = timerViewModel
                        )
                        Destination.Review -> ReviewScreen()
                        Destination.Cards -> CardsScreen(
                            onEditCard = { id ->
                                viewerCardId = id
                                viewerOpen = true
                            },
                            onOpenCard = { id ->
                                viewerCardId = id
                                viewerOpen = true
                            },
                            onOpenTrash = { trashOpen = true }
                        )
                        Destination.Stats -> StatsScreen()
                        Destination.Settings -> SettingsScreen()
                    }
                }
            }
        }
    }
}
