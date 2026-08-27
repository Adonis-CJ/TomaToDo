package com.tomatodo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
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
import com.tomatodo.ui.cards.CardEditScreen
import com.tomatodo.ui.cards.CardsScreen
import com.tomatodo.ui.review.ReviewScreen
import com.tomatodo.ui.settings.SettingsScreen
import com.tomatodo.ui.stats.StatsScreen
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

@Composable
fun MainScreen() {
    var selected by remember { mutableStateOf(Destination.Board) }
    // 轻量路由：卡片撰写页（null = 关闭；cardId 为 null 表示新建）
    var editingCardId by remember { mutableStateOf<Long?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    // 番茄沉浸式全屏：隐藏导航栏（外壳根据此状态只渲染计时页）
    var timerImmersive by remember { mutableStateOf(false) }
    // 番茄钟 ViewModel 提升至 MainScreen，看板可一键启动
    val timerViewModel: com.tomatodo.timer.TimerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    AnimatedContent(
        targetState = editorOpen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "editor",
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) { inEditor ->
        if (inEditor) {
            CardEditScreen(
                cardId = editingCardId,
                onBack = { editorOpen = false }
            )
        } else {
            Row(Modifier.fillMaxSize()) {
                // 沉浸式时隐藏导航栏，实现真·全屏（不重建计时页状态）
                androidx.compose.animation.AnimatedVisibility(visible = !timerImmersive) {
                    Column {
                        NavigationRail {
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
                if (!timerImmersive) VerticalDivider()
                // 内容区
                when (selected) {
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
                            editingCardId = id
                            editorOpen = true
                        }
                    )
                    Destination.Stats -> StatsScreen()
                    Destination.Settings -> SettingsScreen()
                }
            }
        }
    }
}
