package com.tomatodo.timer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.tomatodo.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 悬浮小窗（用户反馈重做）：
 * - 番茄造型：朱砂圆角 + 白色等宽时间 + 暂停/继续按钮 + 关闭
 * - 点击主体返回应用；可拖动
 * - 仅在应用进入后台时出现（由 MainActivity 生命周期控制）
 */
object FloatingWindowManager {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null
    private var timeText: TextView? = null
    private var toggleText: TextView? = null

    fun show(context: Context) {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(context)) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val density = context.resources.displayMetrics.density
        val view = buildView(context, density)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (20 * density).toInt()
            y = (48 * density).toInt()
        }
        layoutParams = params

        setupDrag(view, density)
        wm.addView(view, params)
        overlayView = view

        scope = CoroutineScope(Dispatchers.Main).apply {
            launch {
                TimerController.state.collect { state ->
                    timeText?.text = formatCountdown(state.remainingMillis)
                    toggleText?.text = if (state.isRunning) "暂停" else "继续"
                }
            }
        }
    }

    fun hide() {
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        scope?.cancel()
        scope = null
        windowManager = null
        layoutParams = null
        timeText = null
        toggleText = null
    }

    private fun dp(v: Int, density: Float) = (v * density).toInt()

    private fun buildView(context: Context, density: Float): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20, density), dp(12, density), dp(14, density), dp(12, density))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * density
                setColor(Color.parseColor("#B4553A"))   // 朱砂
            }
            elevation = 8 * density
        }

        // 时间
        val time = TextView(context).apply {
            text = "25:00"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.MONOSPACE
        }
        timeText = time

        // 暂停/继续（白底圆角，朱砂字）
        val toggle = TextView(context).apply {
            text = "暂停"
            setTextColor(Color.parseColor("#B4553A"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * density
                setColor(Color.WHITE)
            }
            setPadding(dp(16, density), dp(6, density), dp(16, density), dp(6, density))
            onClick { if (TimerController.state.value.isRunning) TimerController.pause() else TimerController.start() }
        }
        toggleText = toggle

        // 关闭
        val close = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(16, density), dp(6, density), dp(6, density), dp(6, density))
            onClick { hide() }
        }

        val gap = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10, density), 0)
        }

        container.addView(time)
        container.addView(toggle)
        container.addView(gap)
        container.addView(close)
        return container
    }

    private fun View.onClick(block: () -> Unit) {
        setOnClickListener { block() }
    }

    private fun setupDrag(view: View, density: Float) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false
        val clickSlop = (6 * density).toInt()
        val context = view.context

        view.setOnTouchListener { _, event ->
            val lp = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    false   // 让子 View 优先处理点击
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > clickSlop || kotlin.math.abs(dy) > clickSlop) {
                        dragged = true
                        lp.x = initialX + dx
                        lp.y = initialY + dy
                        windowManager?.updateViewLayout(view, lp)
                    }
                    dragged
                }
                MotionEvent.ACTION_UP -> {
                    // 未拖动且点在容器空白处 → 返回应用
                    if (!dragged) {
                        runCatching {
                            context.startActivity(
                                Intent(context, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                            )
                        }
                    }
                    false
                }
                else -> false
            }
        }
    }
}
