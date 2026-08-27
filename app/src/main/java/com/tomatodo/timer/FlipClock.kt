package com.tomatodo.timer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * 翻页钟（用户反馈重做版）：4 张数字卡相互分离、哑光黑、整卡绕中线翻转的翻页效果。
 */

/** 单张数字卡：数字变化时整卡「从上方翻落」——先翻至背面(-90°)，再平滑翻正到 0°。 */
@Composable
fun FlipCard(
    text: String,
    cardWidth: Dp = 148.dp,
    cardHeight: Dp = 216.dp,
    fontSize: Int = 104,
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(text) {
        if (!initialized) {
            initialized = true
        } else {
            // 从上往下翻：先瞬时翻至竖直背面（顶部朝内），再反向平滑落回正面
            rotation.snapTo(-90f)
            rotation.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
                rotationX = rotation.value
                cameraDistance = 18 * density
            }
            .background(Color(0xFF161618), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF26262A), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        // 数字：卡片翻转过程即显示新值（背面朝上时已切换），不再叠加 fade
        Text(
            text,
            color = Color(0xFFF5F2EC),
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = AppMono
        )
        // 中缝（翻页轴）—— 横贯卡片中心的细线
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0xFF000000))
                .align(Alignment.Center)
        )
    }
}

/** 冒号分隔符 */
@Composable
private fun FlipColon(height: Dp) {
    Box(
        Modifier.height(height).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(Color(0xFF4A4A50), CircleShape)
            )
            Box(Modifier.height(18.dp))
            Box(
                Modifier
                    .size(12.dp)
                    .background(Color(0xFF4A4A50), CircleShape)
            )
        }
    }
}

/**
 * 翻页钟组合：`MM:SS`（分钟可为 3 位），卡片相互分离。
 */
@Composable
fun FlipClock(
    remainingText: String,
    modifier: Modifier = Modifier
) {
    val digits = remainingText.filter { it.isDigit() }
    val hasColon = remainingText.contains(':')
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        digits.forEachIndexed { index, ch ->
            FlipCard(text = ch.toString())
            if (hasColon && index == digits.length - 3) {
                FlipColon(216.dp)
            }
        }
    }
}
