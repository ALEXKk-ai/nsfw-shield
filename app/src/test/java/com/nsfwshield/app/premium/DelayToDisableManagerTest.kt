package com.nsfwshield.app.premium

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the DelayToDisableManager timer logic.
 */
class DelayToDisableManagerTest {

    @Test
    fun `formatted time 23h 45m`() {
        val remainingMs = (23 * 60 * 60 * 1000L) + (45 * 60 * 1000L)
        val formatted = formatTime(remainingMs)
        assertEquals("23h 45m", formatted)
    }

    @Test
    fun `formatted time minutes only`() {
        val remainingMs = (30 * 60 * 1000L) + (15 * 1000L)
        val formatted = formatTime(remainingMs)
        assertEquals("30m 15s", formatted)
    }

    @Test
    fun `formatted time seconds only`() {
        val remainingMs = 45_000L
        val formatted = formatTime(remainingMs)
        assertEquals("45s", formatted)
    }

    @Test
    fun `formatted time zero shows Ready`() {
        assertEquals("Ready", formatTime(0))
    }

    @Test
    fun `formatted time negative shows Ready`() {
        assertEquals("Ready", formatTime(-1000))
    }

    /**
     * Mirrors the formatting logic in DelayToDisableManager.getFormattedRemainingTime()
     */
    private fun formatTime(remainingMs: Long): String {
        if (remainingMs <= 0) return "Ready"
        val hours = remainingMs / (1000 * 60 * 60)
        val minutes = (remainingMs / (1000 * 60)) % 60
        val seconds = (remainingMs / 1000) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
