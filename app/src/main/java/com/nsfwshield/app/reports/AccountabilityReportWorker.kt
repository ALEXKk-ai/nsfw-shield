package com.nsfwshield.app.reports

import android.content.Context
import androidx.work.*
import com.nsfwshield.app.core.logging.ActivityLogger
import com.nsfwshield.app.data.ActivityLogDao
import com.nsfwshield.app.premium.AccountabilityReportService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Worker responsible for periodic accountability report generation.
 * Collects metadata from ActivityLogger and dispatches via ReportService.
 */
class AccountabilityReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val activityLogger: ActivityLogger,
    private val activityLogDao: ActivityLogDao,
    private val reportService: AccountabilityReportService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val config = reportService.partnerConfig.value
            if (!config.isEnabled || config.email.isBlank()) return Result.success()

            val now = System.currentTimeMillis()
            val since = when (config.reportPeriod) {
                AccountabilityReportService.ReportPeriod.DAILY -> now - TimeUnit.DAYS.toMillis(1)
                AccountabilityReportService.ReportPeriod.WEEKLY -> now - TimeUnit.DAYS.toMillis(7)
            }

            // Collect data
            val blockedCount = activityLogDao.getBlockedCountSince(since)
            val dnsBlockedCount = activityLogDao.getDnsBlockCountSince(since)
            val overrideCount = activityLogDao.getOverrideCountSince(since)
            val topCategories = activityLogDao.getTopBlockedCategories(since).map { it.category }

            // Trigger report dispatch
            reportService.generateAndSendReport(
                blockedAttempts = blockedCount + dnsBlockedCount,
                topCategories = topCategories,
                overrideEvents = overrideCount,
                delayTimerTriggers = 0 // In future: track delay-to-disable attempts
            )

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "AccountabilityReportWork"

        /**
         * Schedule the worker based on the current configuration.
         */
        fun schedule(context: Context, period: AccountabilityReportService.ReportPeriod) {
            val interval = when (period) {
                AccountabilityReportService.ReportPeriod.DAILY -> 1L
                AccountabilityReportService.ReportPeriod.WEEKLY -> 7L
            }

            val workRequest = PeriodicWorkRequestBuilder<AccountabilityReportWorker>(
                interval, TimeUnit.DAYS
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
