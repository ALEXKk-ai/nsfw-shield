package com.nsfwshield.app.monitoring

import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App Activity Tracker.
 * Monitors app usage patterns using UsageStatsManager.
 */
@Singleton
class AppActivityTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val totalTimeMs: Long,
        val lastUsed: Long,
        val launchCount: Int
    )

    /**
     * Get app usage stats for the current day.
     */
    fun getTodayUsage(): List<AppUsageInfo> = getUsageForPeriod(Calendar.DAY_OF_YEAR, -1)

    /**
     * Get app usage stats for the past week.
     */
    fun getWeeklyUsage(): List<AppUsageInfo> = getUsageForPeriod(Calendar.WEEK_OF_YEAR, -1)

    /**
     * Get the currently foreground app package name.
     */
    fun getCurrentForegroundApp(): String? {
        val end = System.currentTimeMillis()
        val start = end - 60_000 // Last minute
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, start, end
        )
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    // ─── Private ───

    private fun getUsageForPeriod(calendarField: Int, amount: Int): List<AppUsageInfo> {
        val calendar = Calendar.getInstance()
        val end = calendar.timeInMillis
        calendar.add(calendarField, amount)
        val start = calendar.timeInMillis

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, start, end
        ) ?: return emptyList()

        return stats
            .filter { it.totalTimeInForeground > 0 }
            .map { stat ->
                AppUsageInfo(
                    packageName = stat.packageName,
                    appName = getAppName(stat.packageName),
                    totalTimeMs = stat.totalTimeInForeground,
                    lastUsed = stat.lastTimeUsed,
                    launchCount = 0
                )
            }
            .sortedByDescending { it.totalTimeMs }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
