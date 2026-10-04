package com.kavach.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kavach.MainActivity
import com.kavach.R

object Notification {
    const val CHANNEL_ID = "kavach_status"
    const val NOTIFICATION_ID = 42

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = context.getString(R.string.channel_desc) }
                )
            }
        }
    }

    fun build(context: Context, blockedToday: Int) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText("Blocked $blockedToday today - ${context.getString(R.string.notif_text)}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    context, 0,
                    android.content.Intent(context, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
}
