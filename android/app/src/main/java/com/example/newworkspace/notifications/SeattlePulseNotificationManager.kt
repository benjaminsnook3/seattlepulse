package com.example.newworkspace.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.newworkspace.MainActivity
import com.example.newworkspace.R
import com.example.newworkspace.domain.model.CityAlert
import com.example.newworkspace.domain.model.Severity

/**
 * Builds and posts notifications optimized for lock-screen visibility: the
 * most important information fits into the title and first body line.
 * This is a standard notification, not an attempt to replace the lock screen.
 *
 * Example lock-screen rendering:
 *   Seattle Pulse — HIGH IMPACT
 *   Link delays + Mariners game
 *   Downtown transit may be busy 5-9 PM.
 */
class SeattlePulseNotificationManager(private val context: Context) {

    fun show(alert: CityAlert) {
        NotificationChannels.ensureCreated(context)

        val notification = build(alert)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(alert.id, alert.id.hashCode(), notification)
    }

    fun build(alert: CityAlert): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (alert.category.uppercase()) {
            "IMPACT" -> "Seattle Pulse — ${alert.severity.label()}"
            "TRANSIT" -> "Seattle Pulse — Transit"
            "TRAFFIC" -> "Seattle Pulse — Traffic"
            "SPORTS" -> "Seattle Pulse — Game time"
            "EVENT" -> "Seattle Pulse — Event"
            "WEATHER" -> "Seattle Pulse — Weather"
            else -> "Seattle Pulse"
        }

        // First body line must carry the payload on its own (lock screen shows
        // title + first line collapsed).
        val firstLine = alert.title
        val expanded = alert.message

        return NotificationCompat.Builder(context, NotificationChannels.channelFor(alert.severity))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(firstLine)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$firstLine\n$expanded")
                    .setBigContentTitle(title)
            )
            .setPriority(
                when (alert.severity) {
                    Severity.SEVERE -> NotificationCompat.PRIORITY_HIGH
                    Severity.HIGH -> NotificationCompat.PRIORITY_DEFAULT
                    else -> NotificationCompat.PRIORITY_LOW
                }
            )
            .setCategory(
                when (alert.category.uppercase()) {
                    "TRANSIT", "TRAFFIC" -> Notification.CATEGORY_TRANSPORT
                    "WEATHER" -> Notification.CATEGORY_ALARM
                    else -> Notification.CATEGORY_EVENT
                }
            )
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun Severity.label(): String = when (this) {
        Severity.LOW -> "LOW IMPACT"
        Severity.MODERATE -> "MODERATE IMPACT"
        Severity.HIGH -> "HIGH IMPACT"
        Severity.SEVERE -> "SEVERE IMPACT"
        Severity.UNKNOWN -> "UPDATE"
    }
}
