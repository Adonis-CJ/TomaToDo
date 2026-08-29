package com.tomatodo.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动效令牌（v1.3）：全应用时长/曲线/弹簧统一来源，禁止散写 magic number。
 * 原则：进场用 EaseEnter（内容自下浮入），退场用 EaseExit，按压回食用 springPress。
 */
object Motion {
    // 时长（ms）
    const val DURATION_SHORT = 120
    const val DURATION_MEDIUM = 220
    const val DURATION_LONG = 320
    const val DURATION_XL = 450

    // 曲线
    val EaseStandard: Easing = FastOutSlowInEasing
    val EaseEnter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EaseExit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // 常用规格
    fun <T> enter(duration: Int = DURATION_MEDIUM, delayMillis: Int = 0) =
        tween<T>(duration, delayMillis, EaseEnter)

    fun <T> exit(duration: Int = DURATION_SHORT) =
        tween<T>(duration, easing = EaseExit)

    fun <T> standard(duration: Int = DURATION_LONG) =
        tween<T>(duration, easing = EaseStandard)

    // 按压回弹（缩放 0.98 类微交互）
    fun <T> press() = spring<T>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMediumLow
    )

    // 列表入场阶梯延迟（每项 +35ms，封顶 8 项防长尾）
    fun staggerDelay(index: Int): Int = index.coerceAtMost(8) * 35
}
