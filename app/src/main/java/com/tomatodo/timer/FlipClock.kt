package com.tomatodo.timer

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomatodo.ui.theme.AppMono
import com.tomatodo.ui.theme.Motion

/**
 * 沉浸态时钟（v1.6 重构）：取消 v1.4 分瓣翻页，改为「数字淡出 → 换值 → 淡入」。
 * 换值发生在 alpha=0（不可见）时刻，任一帧只有一个数字可见，从根本上杜绝重影/重叠/闪烁；
 * 卡片尺寸、分段冒号布局与 v1.4 完全一致，不影响沉浸页排版与性能。
 */

// 沉浸态纯黑视觉（独立于「墨·纸」暖色调色板）
private val CardBg = Color(0xFF161618)
private val CardBorder = Color(0xFF26262A)
private val DigitColor = Color(0xFFF5F2EC)
private val ColonColor = Color(0xFF4A4A50)
private val Corner = 16.dp

/**
 * 单个数字卡：值变化时先淡出旧值，在完全透明时切换文本，再淡入新值。
 * 保留 v1.4 公开签名（[cardWidth]/[cardHeight]/[fontSize]），调用方无需改动。
 */
@Composable
fun FlipCard(
    text: String,
    cardWidth: Dp = 148.dp,
    cardHeight: Dp = 216.dp,
    fontSize: Int = 104,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf(text) }
    val digitAlpha = remember { Animatable(1f) }

    // text 未变则不动画；变化则两段式淡出淡入。LaunchedEffect(text) 保证每次变更重跑，
    // 快速连变时旧协程被取消，digitAlpha 停在中间值后由新协程接续，不会残留半透明。
    LaunchedEffect(text) {
        if (text == current) return@LaunchedEffect
        digitAlpha.animateTo(0f, Motion.flipFadeOut())
        current = text
        digitAlpha.animateTo(1f, Motion.flipFadeIn())
    }

    Box(
        modifier
            .width(cardWidth)
            .height(cardHeight)
            .background(CardBg, RoundedCornerShape(Corner))
            .border(1.dp, CardBorder, RoundedCornerShape(Corner)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            current,
            color = DigitColor,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = AppMono,
            modifier = Modifier.graphicsLayer { alpha = digitAlpha.value }
        )
    }
}

/** 冒号分隔符 */
@Composable
private fun FlipColon(height: Dp) {
    Box(
        Modifier
            .height(height)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(ColonColor, CircleShape)
            )
            Box(Modifier.height(18.dp))
            Box(
                Modifier
                    .size(12.dp)
                    .background(ColonColor, CircleShape)
            )
        }
    }
}

/**
 * 时钟组合：按 `:` 分段渲染（`MM:SS` / `H:MM:SS` 均正确），段间插冒号，卡片相互分离。
 */
@Composable
fun FlipClock(
    remainingText: String,
    modifier: Modifier = Modifier
) {
    val segments = remainingText.split(':').filter { it.isNotEmpty() }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, segment ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                segment.forEach { ch -> FlipCard(text = ch.toString()) }
            }
            if (index < segments.lastIndex) FlipColon(216.dp)
        }
    }
}
