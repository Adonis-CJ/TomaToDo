package com.tomatodo.timer

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import com.tomatodo.ui.theme.Motion
import kotlin.math.abs

/**
 * 翻页钟（v1.4 重构）：真实分瓣（split-flap）两段翻转——
 * 上瓣（旧值上半）绕中缝向观者倒下（0 → -90°），露出静态页上的新值上半；
 * 下瓣（新值下半）从垂直位（+90°）绕中缝落位盖住旧值下半。
 * 透视（cameraDistance）+ 随角度渐深的遮光，节奏走 Motion 翻页令牌。
 */

// 沉浸态纯黑视觉（独立于「墨·纸」暖色调色板）
private val CardBg = Color(0xFF161618)
private val CardBorder = Color(0xFF26262A)
private val DigitColor = Color(0xFFF5F2EC)
private val ColonColor = Color(0xFF4A4A50)
private val Corner = 16.dp
private const val FLAP_SHADE_MAX = 0.4f
// cameraDistance 单位是「图层最长边的倍数」（Compose 默认 8 倍 = 几乎无透视），3 倍 = 适度纵深感
private const val CAMERA_DISTANCE = 3f

@Composable
fun FlipCard(
    text: String,
    cardWidth: Dp = 148.dp,
    cardHeight: Dp = 216.dp,
    fontSize: Int = 104,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf(text) }
    var previous by remember { mutableStateOf(text) }
    var animating by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(text) {
        if (text == current) return@LaunchedEffect
        previous = current
        current = text
        animating = true
        progress.snapTo(0f)
        progress.animateTo(0.5f, Motion.flipDown())
        progress.animateTo(1f, Motion.flipLand())
        animating = false
        previous = current
    }

    val p = progress.value

    Box(
        modifier
            .width(cardWidth)
            .height(cardHeight)
            .background(CardBg, RoundedCornerShape(Corner))
            .border(1.dp, CardBorder, RoundedCornerShape(Corner))
    ) {
        // 静态页：上半恒显新值（随上瓣倒下逐渐露出），下半恒显旧值（等待下瓣落位覆盖）
        HalfDigit(current, top = true, cardWidth, cardHeight, fontSize, Modifier.align(Alignment.TopCenter))
        HalfDigit(previous, top = false, cardWidth, cardHeight, fontSize, Modifier.align(Alignment.BottomCenter))

        // 中缝（翻页轴）
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.Black)
                .align(Alignment.Center)
        )

        if (animating) {
            if (p < 0.5f) {
                // 上瓣：旧值上半，绕中缝向观者翻离（0 → -90°）
                FlapHalf(
                    angle = -180f * p,
                    topFlap = true,
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    HalfDigit(previous, top = true, cardWidth, cardHeight, fontSize)
                }
            } else {
                // 下瓣：新值下半，绕中缝自垂直位落位（+90° → 0）
                FlapHalf(
                    angle = 180f * (1f - p),
                    topFlap = false,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    HalfDigit(current, top = false, cardWidth, cardHeight, fontSize)
                }
            }
        }
    }
}

/** 数字的一半（上/下半片）：整卡高的数字按中缝裁切，保留该侧圆角 */
@Composable
private fun HalfDigit(
    value: String,
    top: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    fontSize: Int,
    modifier: Modifier = Modifier
) {
    val half = cardHeight / 2
    Box(
        modifier
            .width(cardWidth)
            .height(half)
            .clip(
                if (top) RoundedCornerShape(topStart = Corner, topEnd = Corner)
                else RoundedCornerShape(bottomStart = Corner, bottomEnd = Corner)
            ),
        contentAlignment = if (top) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        Box(
            Modifier
                .width(cardWidth)
                .height(cardHeight)
                .offset(y = if (top) 0.dp else -half),
            contentAlignment = Alignment.Center
        ) {
            Text(
                value,
                color = DigitColor,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = AppMono
            )
        }
    }
}

/** 翻转瓣（上瓣翻离 / 下瓣落位）：透视旋转 + 随角度渐深的遮光层 */
@Composable
private fun FlapHalf(
    angle: Float,
    topFlap: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier.graphicsLayer {
            rotationX = angle
            transformOrigin = if (topFlap) TransformOrigin(0.5f, 1f) else TransformOrigin(0.5f, 0f)
            cameraDistance = CAMERA_DISTANCE
        }
    ) {
        content()
        val shade = (abs(angle) / 90f).coerceIn(0f, 1f) * FLAP_SHADE_MAX
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = shade))
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
 * 翻页钟组合：按 `:` 分段渲染（`MM:SS` / `H:MM:SS` 均正确），段间插冒号，卡片相互分离。
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
