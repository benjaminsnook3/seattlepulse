package com.seattlepulse.backend.service

import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

enum class NotificationPriority {
    CRITICAL,
    HIGH,
    NORMAL
}

/**
 * Notification priority rules and cooldowns (Phase 12).
 *
 * Cooldowns prevent spam: the same logical alert (keyed by cooldownKey) is not
 * re-sent until its cooldown has elapsed, and a device receives at most
 * MAX_PER_CYCLE notifications per evaluation cycle.
 */
@Service
class NotificationPolicy {

    private data class LastSent(val at: Instant, val priority: NotificationPriority)

    private val lastSent = ConcurrentHashMap<String, LastSent>()

    fun cooldownFor(priority: NotificationPriority): Duration = when (priority) {
        NotificationPriority.CRITICAL -> Duration.ofMinutes(30)
        NotificationPriority.HIGH -> Duration.ofMinutes(60)
        NotificationPriority.NORMAL -> Duration.ofHours(3)
    }

    /**
     * True when this alert should be sent now: not in cooldown, and not
     * preempted by a more recent higher-priority alert for the same key.
     */
    fun shouldSend(cooldownKey: String, priority: NotificationPriority, now: Instant): Boolean {
        val previous = lastSent[cooldownKey] ?: return true
        val elapsed = Duration.between(previous.at, now)
        if (elapsed < cooldownFor(priority)) {
            // A higher-priority escalation is allowed through early.
            return priority == NotificationPriority.CRITICAL && previous.priority != NotificationPriority.CRITICAL
        }
        return true
    }

    fun recordSent(cooldownKey: String, priority: NotificationPriority, now: Instant) {
        lastSent[cooldownKey] = LastSent(now, priority)
    }

    /** Drops cooldown state for keys whose alerts have expired. */
    fun prune(olderThan: Instant) {
        lastSent.entries.removeIf { it.value.at.isBefore(olderThan) }
    }

    companion object {
        const val MAX_PER_CYCLE = 3
    }
}
