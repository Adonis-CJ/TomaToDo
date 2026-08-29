package com.tomatodo.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.tomatodo.MainActivity
import com.tomatodo.R

/**
 * 番茄钟阶段完成 ALARM 通知（v1.2「结束醒目」）：
 * 应用在后台时使用高优先级通道 + 默认闹钟铃声 + FLAG_INSISTENT 循环响铃直到用户处理；
 * 配合 fullScreenIntent 在息屏时尝试亮屏。用户回到应用即取消。
 */
object AlarmNotifications {

    const val CHANNEL_ID = "timer_alarm_channel"
    const val NOTIFICATION_ID = 2

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "番茄钟完成提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "番茄钟阶段结束时的强提醒（循环响铃直到查看）"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 250, 400, 250, 700)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun show(context: Context, title: String, text: String) {
        createChannel(context)
        val openIntent = PendingIntent.getActivity(
            context,
            100,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        // 响铃循环直到用户处理（通知被点开/取消）
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }
}
