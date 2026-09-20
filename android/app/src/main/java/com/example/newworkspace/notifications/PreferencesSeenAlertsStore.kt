package com.example.newworkspace.notifications

import android.content.Context
import java.time.Instant

/**
 * SharedPreferences-backed seen-alerts store. Values are the epoch second the
 * alert was first notified, which lets old entries be pruned.
 */
class PreferencesSeenAlertsStore(context: Context) : SeenAlertsStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun markSeenIfNew(alertId: String): Boolean {
        if (prefs.contains(alertId)) return false
        prefs.edit().putLong(alertId, Instant.now().epochSecond).apply()
        return true
    }

    override fun pruneBefore(cutoff: Instant) {
        val cutoffEpoch = cutoff.epochSecond
        val stale = prefs.all
            .filterValues { (it as? Long ?: Long.MAX_VALUE) < cutoffEpoch }
            .keys
        if (stale.isNotEmpty()) {
            prefs.edit().apply { stale.forEach { remove(it) } }.apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "seattle_pulse_seen_alerts"
    }
}
