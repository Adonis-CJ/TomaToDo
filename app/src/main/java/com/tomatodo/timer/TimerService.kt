package com.tomatodo.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.tomatodo.MainActivity
import com.tomatodo.R
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.model.PomodoroSession
import com.tomatodo.data.model.PomodoroType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 前台服务：后台计时 + 常驻通知 + 完成提示音/震动 */
class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.tomatodo.action.START"
        const val ACTION_TOGGLE = "com.tomatodo.action.TOGGLE"
        const val ACTION_STOP = "com.tomatodo.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settings = com.tomatodo.data.preferences.TimerSettings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        AlarmNotifications.createChannel(this)
        scope.launch {
            (application as com.tomatodo.TomaTodoApplication).container
                .settingsPreferences.settings.collect { s -> settings = s }
        }
        scope.launch {
            // 通知降频至 1Hz（OPTIMIZATION 技术债 #10）：仅秒数/阶段/运行态变化时刷新
            TimerController.state
                .map { Triple(it.phase, it.isRunning, it.remainingMillis / 1000L) }
                .distinctUntilChanged()
                .collect { (phase, running, _) ->
                    // 用户开始下一阶段即撤掉完成强提醒
                    if (running) AlarmNotifications.cancel(this@TimerService)
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(TimerController.state.value))
                }
        }
        scope.launch {
            TimerController.events.collect { event ->
                if (event is TimerController.TimerEvent.PhaseCompleted) {
                    if (event.phase == PomodoroType.FOCUS) recordFocusSession(event)
                    playCompletion(event)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForeground(NOTIFICATION_ID, buildNotification(TimerController.state.value))
            ACTION_TOGGLE -> {
                if (TimerController.state.value.isRunning) TimerController.pause() else TimerController.start()
            }
            ACTION_STOP -> {
                TimerController.reset()
                AlarmNotifications.cancel(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "番茄钟",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "番茄钟计时通知" }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(state: TimerController.TimerState): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleIntent = PendingIntent.getService(
            this, 1, Intent(this, TimerService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, TimerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(phaseLabel(state.phase))
            .setContentText(formatCountdown(state.remainingMillis))
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentIntent(openIntent)
            .addAction(0, if (state.isRunning) "暂停" else "继续", toggleIntent)
            .addAction(0, "结束", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private suspend fun recordFocusSession(event: TimerController.TimerEvent.PhaseCompleted) {
        val db = (application as TomaTodoApplication).container.database
        val taskId = TimerController.state.value.taskId
        // 从关联任务快照科目，供统计页做科目时间分布
        val subjectId = taskId?.let { id -> db.taskDao().getById(id)?.subjectId }
        db.pomodoroSessionDao().insert(
            PomodoroSession(
                taskId = taskId,
                subjectId = subjectId,
                type = PomodoroType.FOCUS,
                startAt = event.startAt,
                endAt = event.endAt,
                plannedDuration = event.plannedMillis / 60_000L,
                actualDuration = event.actualMillis / 60_000L
            )
        )
    }

    /**
     * 阶段完成提醒（v1.2「结束醒目」）：
     * - 前台：ALARM 音频流三连音（媒体音量再小也响）+ 波形长震动，应用内另有全屏浮层；
     * - 后台：ALARM 通知（默认闹钟铃声 + FLAG_INSISTENT 循环响铃 + fullScreenIntent 亮屏），
     *   不再额外放 ToneGenerator 以免双重响铃；静音仅震动模式全程只震动。
     */
    private fun playCompletion(event: TimerController.TimerEvent.PhaseCompleted) {
        vibrate()
        if (settings.vibrationOnly) return // 静音 + 仅震动：只给更长震动
        val minutes = event.plannedMillis / 60_000L
        if (AppForegroundTracker.isForeground) {
            alarmToneTriple()
        } else {
            val title = if (event.phase == PomodoroType.FOCUS) "🍅 专注完成！" else "休息结束！"
            val text = if (event.phase == PomodoroType.FOCUS) {
                "已专注 $minutes 分钟，该休息了"
            } else {
                "休息了 $minutes 分钟，开始下一个专注吧"
            }
            AlarmNotifications.show(this, title, text)
        }
    }

    /** ALARM 流三连音：比媒体流穿透力强，图书馆耳机场景也能听见 */
    private fun alarmToneTriple() {
        try {
            val toneGen = ToneGenerator(
                AudioManager.STREAM_ALARM,
                (settings.volume * 100).toInt().coerceIn(0, 100)
            )
            val tone = when (settings.ringtoneId) {
                "gentle" -> ToneGenerator.TONE_PROP_BEEP
                "crisp" -> ToneGenerator.TONE_CDMA_PIP
                else -> ToneGenerator.TONE_PROP_BEEP2
            }
            scope.launch {
                repeat(3) {
                    runCatching { toneGen.startTone(tone, 260) }
                    kotlinx.coroutines.delay(480)
                }
                runCatching { toneGen.release() }
            }
        } catch (_: Exception) {
            // 忽略音频设备异常
        }
    }

    private fun vibrate() {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        // 三段递进波形：比单次 500ms 更难被忽略
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 350, 220, 350, 220, 700),
                -1
            )
        )
    }
}
