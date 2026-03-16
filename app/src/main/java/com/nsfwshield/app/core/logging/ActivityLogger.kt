package com.nsfwshield.app.core.logging

import com.nsfwshield.app.data.ActivityLogDao
import com.nsfwshield.app.data.ActivityLogEntry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Activity Logger.
 * Central logging facility for all detection and blocking events.
 * All logs are stored locally and never transmitted externally.
 */
@Singleton
class ActivityLogger @Inject constructor(
    private val activityLogDao: ActivityLogDao
) {
    // In-memory cache to prevent log flooding (deduplication)
    private val lastLoggedMap = ConcurrentHashMap<String, Long>()
    
    companion object {
        private const val COOLDOWN_MS = 60_000L // 60 second cooldown per unique event
    }
    /**
     * Log a blocked content event.
     */
    suspend fun logBlockedEvent(
        category: String,
        source: String,
        score: Float,
        profileId: String,
        appPackage: String? = null,
        domain: String? = null,
        action: String = "BLOCK"
    ) {
        val key = "ACC:$appPackage:$category"
        val now = System.currentTimeMillis()
        val lastLogged = lastLoggedMap.getOrDefault(key, 0L)
        
        if (now - lastLogged < COOLDOWN_MS) return // Skip if in cooldown

        val entry = ActivityLogEntry(
            eventType = "BLOCKED",
            category = category,
            source = source,
            confidenceScore = score,
            profileId = profileId,
            appPackage = appPackage,
            domain = domain,
            actionTaken = action
        )
        activityLogDao.insertLog(entry)
        lastLoggedMap[key] = now
    }

    /**
     * Log a DNS block event.
     */
    suspend fun logDnsBlock(domain: String, profileId: String, category: String = "adult") {
        val key = "DNS:$domain"
        val now = System.currentTimeMillis()
        val lastLogged = lastLoggedMap.getOrDefault(key, 0L)
        
        if (now - lastLogged < COOLDOWN_MS) return // Skip if in cooldown

        // Map internal category name to a user-friendly labels for accountability
        val displayCategory = when (category) {
            "adult" -> "Adult Content Site"
            "gambling" -> "Gambling Site"
            "drugs" -> "Drug-related Site"
            "gore" -> "Violence/Gore Site"
            "self_harm" -> "Self-Harm Site"
            "hate_speech" -> "Hate Speech Site"
            "custom" -> "Custom Blocklist Site"
            else -> "Blocked Site"
        }

        val entry = ActivityLogEntry(
            eventType = "DNS_BLOCK",
            category = displayCategory,
            source = "DNS_FILTER",
            confidenceScore = 1.0f,
            profileId = profileId,
            domain = domain,
            actionTaken = "BLOCK"
        )
        activityLogDao.insertLog(entry)
        lastLoggedMap[key] = now
    }

    /**
     * Log a user override event (bypassed a block).
     */
    suspend fun logOverride(
        category: String,
        profileId: String,
        appPackage: String? = null
    ) {
        val entry = ActivityLogEntry(
            eventType = "OVERRIDE",
            category = category,
            source = "USER",
            confidenceScore = 0f,
            profileId = profileId,
            appPackage = appPackage,
            actionTaken = "ALLOW",
            wasOverridden = true
        )
        activityLogDao.insertLog(entry)
    }

    /**
     * Get blocked attempt count for a time period.
     */
    suspend fun getBlockedCount(sinceMs: Long): Int {
        return activityLogDao.getBlockedCountSince(sinceMs)
    }

    /**
     * Get a reactive Flow of blocked attempt counts for a time period.
     */
    fun getBlockedCountFlow(sinceMs: Long): kotlinx.coroutines.flow.Flow<Int> {
        return activityLogDao.getBlockedCountSinceFlow(sinceMs)
    }

    /**
     * Get override count for a time period.
     */
    suspend fun getOverrideCount(sinceMs: Long): Int {
        return activityLogDao.getOverrideCountSince(sinceMs)
    }

    /**
     * Clean up old logs based on subscription tier.
     * Free: 7 days, Premium: 90 days.
     */
    suspend fun cleanupOldLogs(isPremium: Boolean) {
        val retentionDays = if (isPremium) 90L else 7L
        val cutoff = System.currentTimeMillis() -
                TimeUnit.DAYS.toMillis(retentionDays)
        activityLogDao.deleteLogsBefore(cutoff)
    }
}
