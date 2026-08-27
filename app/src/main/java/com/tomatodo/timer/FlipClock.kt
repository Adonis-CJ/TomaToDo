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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomatodo.ui.theme.AppMono

/**
 * 翻页钟（用户反馈重做版）：4 张数字卡相互分离、哑光黑、整卡绕中线翻转的翻页效果。
 */

/**
 * 单张数字卡：台历翻页——只动上半片绕水平中轴向下翻（0 → -180°），下半片静止。
 * 后半段换新值并加镜像抵消（避免翻过去时反字），翻页完成整卡更新。
 */
@Composable
fun FlipCard(
    text: String,
    cardWidth: Dp = 148.dp,
    cardHeight: Dp = 216.dp,
    fontSize: Int = 104,
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableStateOf(text) }
    val flip = remember { Animatable(0f) }

    LaunchedEffect(text) {
        if (shown == text) return@LaunchedEffect
        flip.snapTo(0f)
        flip.animateTo(-180f, tween(420, easing = FastOutSlowInEasing))
        shown = text
        flip.snapTo(0f)
    }

    val half = cardHeight / 2
    // 上半翻页内容：前半(0..-90)显示旧值，后半(-90..-180)显示新值
    val topContent = if (flip.value <= -90f) text else shown

    Box(
        modifier
            .width(cardWidth)
            .height(cardHeight)
            .background(Color(0xFF161618), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF26262A), RoundedCornerShape(16.dp))
    ) {
        // ------------------ 下半片（静止，显示当前值旧半）------------------
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(half)
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    shown, color = Color(0xFFF5F2EC), fontSize = fontSize.sp,
                    fontWeight = FontWeight.Medium, fontFamily = AppMono
                )
            }
        }
        // ------------------ 上半片（向下翻页）------------------
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(half)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .graphicsLayer {
                    rotationX = flip.value
                    transformOrigin = TransformOrigin(0.5f, 1f) // 绕底边（水平中轴）旋转
                },
            contentAlignment = Alignment.Center
        ) {
            // 后半段(已翻过去)再镜像一次，让新值保持正立
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (flip.value <= -90f) rotationX = 180f // 翻过半后抵消上下颠倒
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    topContent, color = Color(0xFFF5F2EC), fontSize = fontSize.sp,
                    fontWeight = FontWeight.Medium, fontFamily = AppMono
                )
            }
        }
        // 中缝（翻页轴）
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
