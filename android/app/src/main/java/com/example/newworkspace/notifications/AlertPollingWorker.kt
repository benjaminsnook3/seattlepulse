package com.example.newworkspace.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.newworkspace.domain.model.CityAlert
import com.example.newworkspace.domain.model.Outcome
import com.example.newworkspace.domain.repository.SeattleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

/**
 * Periodically pulls the backend's already-prioritized alert list and posts
 * notifications for alerts not seen before. The backend owns spam prevention
 * (cooldowns, per-cycle caps); the client only de-duplicates locally so a
 * restart does not re-notify the same alert.
 */
@HiltWorker
class AlertPollingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SeattleRepository,
    private val notificationManager: SeattlePulseNotificationManager,
    private val seenAlertsStore: SeenAlertsStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (val outcome = repository.getAlerts()) {
            is Outcome.Failure -> Result.retry()
            is Outcome.Success -> {
                val now = Instant.now()
                val fresh = outcome.data.filter { alert ->
                    val notExpired = alert.expiresAt?.isAfter(now) ?: true
                    notExpired && seenAlertsStore.markSeenIfNew(alert.id)
                }
                fresh.forEach { notificationManager.show(it) }
                seenAlertsStore.pruneBefore(now.minusSeconds(SEEN_RETENTION_SECONDS))
                Result.success()
            }
        }
    }

    companion object {
        const val WORK_NAME = "seattle-pulse-alert-polling"
        private const val SEEN_RETENTION_SECONDS = 7 * 24 * 60 * 60L
    }
}

/**
 * Remembers alert ids already notified so polling does not re-alert the same
 * item after each cycle or app restart.
 */
interface SeenAlertsStore {
    /** Returns true the first time [alertId] is seen; false afterwards. */
    fun markSeenIfNew(alertId: String): Boolean
    fun pruneBefore(cutoff: Instant)
}
