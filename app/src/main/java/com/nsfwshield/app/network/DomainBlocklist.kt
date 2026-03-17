package com.nsfwshield.app.network

import android.content.Context
import com.nsfwshield.app.data.BlockedDomainDao
import com.nsfwshield.app.data.BlockedDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain blocklist manager.
 * Ships with a curated adult/explicit domain list, supports custom entries
 * (Premium), and remotely updatable via signed blocklist files.
 */
@Singleton
class DomainBlocklist @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedDomainDao: BlockedDomainDao
) {
    // In-memory cache for fast lookups during DNS interception
    private val blockedDomains = mutableSetOf<String>()
    private var isInitialized = false

    companion object {
        // Curated built-in blocklist of known adult content domains
        // Based on Steven Black hosts list and similar sources
        private val BUILTIN_DOMAINS = setOf(
            // Adult content aggregators
            "pornhub.com", "www.pornhub.com",
            "xvideos.com", "www.xvideos.com",
            "xnxx.com", "www.xnxx.com",
            "xhamster.com", "www.xhamster.com",
            "redtube.com", "www.redtube.com",
            "youporn.com", "www.youporn.com",
            "tube8.com", "www.tube8.com",
            "spankbang.com", "www.spankbang.com",
            "eporner.com", "www.eporner.com",
            "hentaihaven.xxx",
            "rule34.xxx",
            "nhentai.net",
            "e-hentai.org",
            "gelbooru.com",
            "danbooru.donmai.us",

            // Cam sites
            "chaturbate.com",
            "bongacams.com",
            "stripchat.com",
            "myfreecams.com",
            "cam4.com",
            "livejasmin.com",

            // Adult social/subscription
            "onlyfans.com",
            "fansly.com",
            "manyvids.com",

            // Gambling
            "bet365.com",
            "pokerstars.com",
            "888casino.com",
            "williamhill.com",
            "betfair.com",
            "draftkings.com",
            "fanduel.com",
            "bovada.lv",

            // Drug-related
            "erowid.org",
            "silkroad.link",

            // Gore/shock content
            "bestgore.com",
            "liveleak.com",
            "theync.com",

            // Self-harm and Eating Disorders (pro-ana/mia)
            "myproana.com",
            "prothinker.com",
            "thincompetitions.com",
            "suicideforum.com",

            // Hate Speech / Extremism
            "stormfront.org",
            "dailystormer.name",
            "theisraelofgodrc.com",
            "newblackpanther.com"
        )

        // Category mappings for the built-in list
        private val DOMAIN_CATEGORIES = mapOf(
            "adult" to setOf(
                "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com",
                "redtube.com", "youporn.com", "tube8.com", "spankbang.com",
                "eporner.com", "chaturbate.com", "bongacams.com", "stripchat.com",
                "myfreecams.com", "cam4.com", "livejasmin.com", "onlyfans.com",
                "fansly.com", "manyvids.com", "hentaihaven.xxx", "rule34.xxx",
                "nhentai.net", "e-hentai.org", "gelbooru.com", "danbooru.donmai.us"
            ),
            "gambling" to setOf(
                "bet365.com", "pokerstars.com", "888casino.com", "williamhill.com",
                "betfair.com", "draftkings.com", "fanduel.com", "bovada.lv"
            ),
            "drugs" to setOf("erowid.org", "silkroad.link"),
            "gore" to setOf("bestgore.com", "liveleak.com", "theync.com"),
            "self_harm" to setOf("myproana.com", "prothinker.com", "thincompetitions.com", "suicideforum.com"),
            "hate_speech" to setOf("stormfront.org", "dailystormer.name", "theisraelofgodrc.com", "newblackpanther.com")
        )
    }

    /**
     * Initialize the blocklist by loading built-in domains into memory
     * and syncing with the database.
     */
    suspend fun initialize() {
        if (isInitialized) return

        // Load built-in domains into memory and database
        blockedDomains.addAll(BUILTIN_DOMAINS)
        blockedDomains.addAll(BUILTIN_DOMAINS.map { "www.$it" })

        // Persist built-in domains to database
        val builtinEntries = BUILTIN_DOMAINS.map { domain ->
            val category = DOMAIN_CATEGORIES.entries
                .firstOrNull { (_, domains) -> domains.contains(domain) }
                ?.key ?: "adult"

            BlockedDomain(
                domain = domain,
                category = category,
                source = "builtin",
                isCustom = false
            )
        }
        blockedDomainDao.insertDomains(builtinEntries)

        isInitialized = true
    }

    /**
     * Get the category for a built-in domain.
     */
    fun getBuiltinCategory(domain: String): String? {
        val normalizedDomain = domain.lowercase().trim()
        
        // Direct match
        if (normalizedDomain in BUILTIN_DOMAINS) {
            return DOMAIN_CATEGORIES.entries
                .firstOrNull { (_, domains) -> domains.contains(normalizedDomain) }
                ?.key
        }
        
        // Subdomain matching
        val parts = normalizedDomain.split(".")
        for (i in 1 until parts.size - 1) {
            val parentDomain = parts.subList(i, parts.size).joinToString(".")
            if (parentDomain in BUILTIN_DOMAINS) {
                return DOMAIN_CATEGORIES.entries
                    .firstOrNull { (_, domains) -> domains.contains(parentDomain) }
                    ?.key
            }
        }
        return null
    }

    /**
     * Fast in-memory check if a domain is blocked.
     */
    fun isBlocked(domain: String): Boolean {
        val normalizedDomain = domain.lowercase().trim()
        
        // Direct match
        if (normalizedDomain in blockedDomains) return true
        
        // Check if any parent domain is blocked (subdomain matching)
        val parts = normalizedDomain.split(".")
        for (i in 1 until parts.size - 1) {
            val parentDomain = parts.subList(i, parts.size).joinToString(".")
            if (parentDomain in blockedDomains) return true
        }

        return false
    }

    /**
     * Get all custom domains as a Flow.
     */
    fun getCustomDomains(): Flow<List<BlockedDomain>> = blockedDomainDao.getCustomDomains()

    /**
     * Add a custom domain to the blocklist (Premium feature).
     */
    suspend fun addCustomDomain(domain: String, category: String = "custom") {
        val entry = BlockedDomain(
            domain = domain.lowercase(),
            category = category,
            source = "custom",
            isCustom = true
        )
        blockedDomainDao.insertDomain(entry)
        blockedDomains.add(domain.lowercase())
    }

    /**
     * Remove a custom domain from the blocklist.
     */
    suspend fun removeCustomDomain(domain: String) {
        val entry = BlockedDomain(domain = domain.lowercase(), category = "", source = "custom", isCustom = true)
        blockedDomainDao.deleteDomain(entry)
        blockedDomains.remove(domain.lowercase())
    }

    /**
     * Update the blocklist from a remote categorized list.
     * Each category maps to a list of domains.
     */
    suspend fun updateFromRemoteCategorized(categorizedDomains: Map<String, List<String>>) {
        // Clear old remote updates
        blockedDomainDao.deleteBySource("remote_update")

        val allEntries = mutableListOf<BlockedDomain>()
        categorizedDomains.forEach { (category, domains) ->
            domains.forEach { domain ->
                allEntries.add(
                    BlockedDomain(
                        domain = domain.lowercase(),
                        category = category,
                        source = "remote_update",
                        isCustom = false
                    )
                )
            }
        }

        blockedDomainDao.insertDomains(allEntries)

        // Update in-memory cache
        blockedDomains.addAll(allEntries.map { it.domain })
    }

    /**
     * Update the blocklist from a remote signed file.
     * @deprecated Use updateFromRemoteCategorized instead.
     */
    suspend fun updateFromRemote(domains: List<String>, category: String = "remote_update") {
        val entries = domains.map { domain ->
            BlockedDomain(
                domain = domain.lowercase(),
                category = category,
                source = "remote_update",
                isCustom = false
            )
        }

        // Clear old remote updates and insert new ones
        blockedDomainDao.deleteBySource("remote_update")
        blockedDomainDao.insertDomains(entries)

        // Update in-memory cache
        blockedDomains.addAll(domains.map { it.lowercase() })
    }

    /**
     * Get the total number of blocked domains.
     */
    suspend fun getTotalCount(): Int = blockedDomainDao.getTotalCount()
}
