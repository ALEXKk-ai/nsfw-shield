package com.nsfwshield.app.network

import android.content.Context
import android.util.Log
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules or cancels the periodic blocklist update worker
 * based on user preferences.
 */
@Singleton
class BlocklistUpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BlocklistScheduler"
    }

    /**
     * Schedule periodic blocklist updates.
     * @param wifiOnly If true, only run when connected to Wi-Fi.
     */
    fun schedule(wifiOnly: Boolean = true) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val workRequest = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BlocklistUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.i(TAG, "Blocklist auto-update scheduled (Wi-Fi only: $wifiOnly)")
    }

    /**
     * Cancel any scheduled blocklist update work.
     */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(BlocklistUpdateWorker.WORK_NAME)
        Log.i(TAG, "Blocklist auto-update cancelled")
    }
}
