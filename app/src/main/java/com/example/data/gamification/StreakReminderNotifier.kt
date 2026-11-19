package com.example.data.gamification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.model.StreakNotificationLog

/**
 * Handles Android system notification delivery for Login Streak reminders (08:00, 12:00, 17:00 WIB)
 * when notification permission is granted. Safe no-op in tests or when permission is not granted.
 */
object StreakReminderNotifier {
    private const val CHANNEL_ID = "azril_streak_reminders_wib"
    private const val CHANNEL_NAME = "Pengingat Login Streak (WIB)"
    private const val CHANNEL_DESC = "Pengingat harian pukul 08.00, 12.00, dan 17.00 WIB agar streak Azril Clas tidak terputus."

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = CHANNEL_DESC
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun postReminderNotification(context: Context, log: StreakNotificationLog): Boolean {
        if (!hasNotificationPermission(context)) return false
        return runCatching {
            ensureChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("${log.slot.title} (${log.slot.timeLabel})")
                .setContentText(log.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(log.message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            val notifId = (log.studentId + log.reminderDateWib + log.slot.name).hashCode()
            NotificationManagerCompat.from(context).notify(notifId, notification)
            true
        }.getOrDefault(false)
    }
}
