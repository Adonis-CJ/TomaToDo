package com.tomatodo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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

    Row(Modifier.fillMaxSize()) {
        // 左侧导航栏（平板 Navigation Rail）
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
        VerticalDivider()
        // 内容区（占位，后续替换为各功能屏）
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
