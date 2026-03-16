package com.nsfwshield.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Activity log entry stored in the local encrypted database.
 */
@Entity(tableName = "activity_logs")
data class ActivityLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,          // BLOCKED, ALLOWED, OVERRIDE, VPN_BLOCK, DNS_BLOCK
    val category: String,           // Content category
    val source: String,             // Detection source
    val confidenceScore: Float,
    val profileId: String,
    val appPackage: String? = null,
    val domain: String? = null,
    val actionTaken: String,        // BLUR, REPLACE, BLOCK, ALLOW
    val wasOverridden: Boolean = false
)

@Dao
interface ActivityLogDao {

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<ActivityLogEntry>>

    @Query("SELECT * FROM activity_logs WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getLogsSince(since: Long): Flow<List<ActivityLogEntry>>

    @Query("SELECT * FROM activity_logs WHERE eventType = 'BLOCKED' ORDER BY timestamp DESC LIMIT :limit")
    fun getBlockedEvents(limit: Int = 50): Flow<List<ActivityLogEntry>>

    @Query("SELECT COUNT(*) FROM activity_logs WHERE eventType = 'BLOCKED' AND timestamp >= :since")
    suspend fun getBlockedCountSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM activity_logs WHERE eventType = 'BLOCKED' AND timestamp >= :since")
    fun getBlockedCountSinceFlow(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM activity_logs WHERE wasOverridden = 1 AND timestamp >= :since")
    suspend fun getOverrideCountSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM activity_logs WHERE wasOverridden = 1 AND timestamp >= :since")
    fun getOverrideCountSinceFlow(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM activity_logs WHERE eventType = 'DNS_BLOCK' AND timestamp >= :since")
    suspend fun getDnsBlockCountSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM activity_logs WHERE eventType = 'DNS_BLOCK' AND timestamp >= :since")
    fun getDnsBlockCountSinceFlow(since: Long): Flow<Int>

    @Query("SELECT category, COUNT(*) as count FROM activity_logs WHERE eventType = 'BLOCKED' AND timestamp >= :since GROUP BY category ORDER BY count DESC")
    suspend fun getTopBlockedCategories(since: Long): List<CategoryCount>

    @Query("SELECT category, COUNT(*) as count FROM activity_logs WHERE eventType = 'BLOCKED' AND timestamp >= :since GROUP BY category ORDER BY count DESC")
    fun getTopBlockedCategoriesFlow(since: Long): Flow<List<CategoryCount>>

    @Insert
    suspend fun insertLog(entry: ActivityLogEntry)

    @Query("DELETE FROM activity_logs WHERE timestamp < :before")
    suspend fun deleteLogsBefore(before: Long)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAllLogs()
}

/**
 * Projection for category aggregation queries.
 */
data class CategoryCount(
    val category: String,
    val count: Int
)
