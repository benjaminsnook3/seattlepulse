package com.example.newworkspace.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the repeating alert-polling job. Periodic work is floored at 15
 * minutes by WorkManager; the backend refreshes every 5 minutes, so alerts
 * reach the device within one polling window.
 */
@Singleton
class AlertPollingScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun start() {
        val request = PeriodicWorkRequestBuilder<AlertPollingWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AlertPollingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(AlertPollingWorker.WORK_NAME)
    }
}
