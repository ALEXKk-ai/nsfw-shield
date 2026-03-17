package com.nsfwshield.app.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safe Search Enforcer.
 * Ensures safe search is active on major search engines by injecting
 * the appropriate parameters into URLs.
 */
@Singleton
class SafeSearchEnforcer @Inject constructor() {

    /**
     * Safe search parameter configurations per platform.
     */
    data class SafeSearchConfig(
        val domain: String,
        val parameter: String,
        val value: String
    )

    companion object {
        val SAFE_SEARCH_CONFIGS = listOf(
            SafeSearchConfig("google.com", "safe", "active"),
            SafeSearchConfig("google.", "safe", "active"),    // Covers regional Google domains
            SafeSearchConfig("youtube.com", "safe", "active"),
            SafeSearchConfig("bing.com", "adlt", "strict"),
            SafeSearchConfig("duckduckgo.com", "kp", "1"),
            SafeSearchConfig("yandex.com", "fyandex", "1"),
            SafeSearchConfig("yahoo.com", "vm", "r")
        )

        // DNS override IPs for enforcing safe search at network level
        val SAFE_SEARCH_DNS = mapOf(
            "www.google.com" to "216.239.38.120",     // forcesafesearch.google.com
            "www.youtube.com" to "216.239.38.120",     // restrict.youtube.com
            "m.youtube.com" to "216.239.38.120",
            "youtubei.googleapis.com" to "216.239.38.120",
            "youtube.googleapis.com" to "216.239.38.120",
            "www.youtube-nocookie.com" to "216.239.38.120"
        )
    }

    /**
     * Check if a URL needs safe search enforcement.
     */
    fun needsEnforcement(url: String): Boolean {
        val normalizedUrl = url.lowercase()
        return SAFE_SEARCH_CONFIGS.any { config ->
            normalizedUrl.contains(config.domain)
        }
    }

    /**
     * Inject safe search parameters into a URL.
     */
    fun enforceUrl(url: String): String {
        val normalizedUrl = url.lowercase()

        for (config in SAFE_SEARCH_CONFIGS) {
            if (normalizedUrl.contains(config.domain)) {
                return injectParameter(url, config.parameter, config.value)
            }
        }

        return url
    }

    /**
     * Get safe search DNS override for a domain, if applicable.
     */
    fun getSafeSearchDns(domain: String): String? {
        return SAFE_SEARCH_DNS[domain.lowercase()]
    }

    /**
     * Check if a DNS query should be redirected for safe search.
     */
    fun shouldRedirectDns(domain: String): Boolean {
        return domain.lowercase() in SAFE_SEARCH_DNS
    }

    // ─── Private helpers ───

    private fun injectParameter(url: String, param: String, value: String): String {
        val paramPair = "$param=$value"

        return if (url.contains("?")) {
            // Check if parameter already exists
            if (url.contains("$param=")) {
                // Replace existing parameter
                url.replace(Regex("$param=[^&]*"), paramPair)
            } else {
                // Append parameter
                "$url&$paramPair"
            }
        } else {
            // Add first parameter
            "$url?$paramPair"
        }
    }
}
