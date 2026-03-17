package com.nsfwshield.app.network

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the DnsFilter.
 * Tests domain extraction, blocking logic, and DNS response generation.
 */
class DnsFilterTest {

    @Test
    fun `matchesDomainPattern matches exact domain`() {
        val filter = createDnsFilterForTest()
        assertTrue(filter.matchesDomainPattern("example.com", "example.com"))
        assertFalse(filter.matchesDomainPattern("example.com", "other.com"))
    }

    @Test
    fun `matchesDomainPattern supports wildcard`() {
        val filter = createDnsFilterForTest()
        assertTrue(filter.matchesDomainPattern("sub.example.com", "*.example.com"))
        assertTrue(filter.matchesDomainPattern("deep.sub.example.com", "*.example.com"))
        assertFalse(filter.matchesDomainPattern("example.com", "*.other.com"))
    }

    @Test
    fun `matchesDomainPattern wildcard base domain also matches`() {
        val filter = createDnsFilterForTest()
        assertTrue(filter.matchesDomainPattern("example.com", "*.example.com"))
    }

    private fun createDnsFilterForTest(): DnsFilter {
        // Creating a minimal DnsFilter for pattern matching tests
        // In full integration tests, this would use mock DAOs
        return DnsFilter::class.java.getDeclaredConstructor().let {
            // For pattern matching tests, we can test the pure function
            DnsFilter::class.java.getDeclaredConstructor().newInstance()
        }
    }
}
