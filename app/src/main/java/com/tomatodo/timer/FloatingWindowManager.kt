package com.tomatodo.timer

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 悬浮小窗管理器（PRD §6.7）：屏幕上方小浮窗，显示倒计时并可控制计时。
 * 需要「显示在其他应用上层」权限（SYSTEM_ALERT_WINDOW）。
 */
object FloatingWindowManager {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null
    private var timeText: TextView? = null
    private var toggleButton: Button? = null

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
            x = 16
            y = (120 * density).toInt()
        }
        layoutParams = params

        setupDrag(view, density)
        wm.addView(view, params)
        overlayView = view

        scope = CoroutineScope(Dispatchers.Main).apply {
            launch {
                TimerController.state.collect { state ->
                    timeText?.text = "${phaseLabel(state.phase)} ${formatCountdown(state.remainingMillis)}"
                    toggleButton?.text = if (state.isRunning) "暂停" else "继续"
                }
            }
        }
    }

    fun hide() {
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        scope?.cancel()
        scope = null
        windowManager = null
        layoutParams = null
        timeText = null
        toggleButton = null
    }

    private fun buildView(context: Context, density: Float): View {
        val pad = (12 * density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, (8 * density).toInt(), pad, (8 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * density
                setColor(Color.parseColor("#FDFCF8"))
                setStroke((1 * density).toInt(), Color.parseColor("#E8E3D8"))
            }
        }

        val time = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#2B2A26"))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        timeText = time

        val toggle = Button(context).apply {
            text = "暂停"
            textSize = 12f
            setOnClickListener {
                if (TimerController.state.value.isRunning) TimerController.pause() else TimerController.start()
            }
        }
        toggleButton = toggle

        val close = Button(context).apply {
            text = "关闭"
            textSize = 12f
            setOnClickListener { hide() }
        }

        container.addView(time)
        container.addView(toggle)
        container.addView(close)
        return container
    }

    private fun setupDrag(view: View, density: Float) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        val clickSlop = (4 * density).toInt()

        view.setOnTouchListener { _, event ->
            val lp = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > clickSlop || kotlin.math.abs(dy) > clickSlop) {
                        lp.x = initialX + dx
                        lp.y = initialY + dy
                        windowManager?.updateViewLayout(view, lp)
                    }
                    true
                }
                else -> false
            }
        }
    }
}
