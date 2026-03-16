package com.nsfwshield.app.network

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the SafeSearchEnforcer.
 */
class SafeSearchEnforcerTest {

    private val enforcer = SafeSearchEnforcer()

    @Test
    fun `detects Google URL needs enforcement`() {
        assertTrue(enforcer.needsEnforcement("https://www.google.com/search?q=test"))
    }

    @Test
    fun `detects YouTube URL needs enforcement`() {
        assertTrue(enforcer.needsEnforcement("https://www.youtube.com/results?search_query=test"))
    }

    @Test
    fun `detects Bing URL needs enforcement`() {
        assertTrue(enforcer.needsEnforcement("https://www.bing.com/search?q=test"))
    }

    @Test
    fun `non-search URL does not need enforcement`() {
        assertFalse(enforcer.needsEnforcement("https://www.example.com/page"))
    }

    @Test
    fun `enforceUrl adds safe search to Google`() {
        val result = enforcer.enforceUrl("https://www.google.com/search?q=test")
        assertTrue("Should contain safe=active", result.contains("safe=active"))
    }

    @Test
    fun `enforceUrl adds safe search to Bing`() {
        val result = enforcer.enforceUrl("https://www.bing.com/search?q=test")
        assertTrue("Should contain adlt=strict", result.contains("adlt=strict"))
    }

    @Test
    fun `enforceUrl replaces existing parameter`() {
        val result = enforcer.enforceUrl("https://www.google.com/search?q=test&safe=off")
        assertTrue("Should replace safe=off with safe=active", result.contains("safe=active"))
        assertFalse("Should not contain safe=off", result.contains("safe=off"))
    }

    @Test
    fun `getSafeSearchDns returns IP for YouTube`() {
        val dns = enforcer.getSafeSearchDns("www.youtube.com")
        assertNotNull("Should return safe search DNS for YouTube", dns)
    }

    @Test
    fun `getSafeSearchDns returns null for unknown domain`() {
        val dns = enforcer.getSafeSearchDns("www.example.com")
        assertNull("Should return null for unknown domain", dns)
    }
}
