package com.example.newworkspace.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.newworkspace.domain.model.Severity

/**
 * Notification channels. Importance is set once at creation (Android ignores
 * later changes), so each priority tier gets its own channel.
 */
object NotificationChannels {

    const val CHANNEL_CRITICAL = "seattle_pulse_critical"
    const val CHANNEL_HIGH = "seattle_pulse_high"
    const val CHANNEL_NORMAL = "seattle_pulse_normal"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CRITICAL,
                "Major disruptions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Major Link/Metro disruptions and severe impact"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HIGH,
                "Important updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Traffic incidents, weather changes, impact changes"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NORMAL,
                "Games and events",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Games and events starting soon"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
    }

    fun channelFor(severity: Severity): String = when (severity) {
        Severity.SEVERE -> CHANNEL_CRITICAL
        Severity.HIGH -> CHANNEL_HIGH
        else -> CHANNEL_NORMAL
    }
}
