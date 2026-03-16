package com.nsfwshield.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Blocked domain entry for DNS filtering.
 */
@Entity(tableName = "blocked_domains")
data class BlockedDomain(
    @PrimaryKey
    val domain: String,
    val category: String,           // adult, gambling, malware, etc.
    val source: String = "builtin", // builtin, custom, remote_update
    val isCustom: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface BlockedDomainDao {

    @Query("SELECT * FROM blocked_domains ORDER BY domain ASC")
    fun getAllDomains(): Flow<List<BlockedDomain>>

    @Query("SELECT * FROM blocked_domains WHERE isCustom = 1 ORDER BY domain ASC")
    fun getCustomDomains(): Flow<List<BlockedDomain>>

    @Query("SELECT COUNT(*) FROM blocked_domains WHERE domain = :domain")
    suspend fun isDomainBlocked(domain: String): Int

    @Query("SELECT * FROM blocked_domains WHERE domain LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchDomains(query: String): List<BlockedDomain>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDomain(domain: BlockedDomain)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDomains(domains: List<BlockedDomain>)

    @Delete
    suspend fun deleteDomain(domain: BlockedDomain)

    @Query("DELETE FROM blocked_domains WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT category FROM blocked_domains WHERE domain = :domain")
    suspend fun getDomainCategory(domain: String): String?

    @Query("SELECT COUNT(*) FROM blocked_domains")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM blocked_domains")
    fun getTotalCountFlow(): Flow<Int>
}
