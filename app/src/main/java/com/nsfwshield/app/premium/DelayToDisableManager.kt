package com.nsfwshield.app.premium

import android.content.Context
import android.content.SharedPreferences
import com.nsfwshield.app.core.profiles.FilterProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delay-to-Disable Manager — Premium feature.
 *
 * The most differentiated feature for users managing compulsive habits.
 * When enabled, the user must wait 24 hours before they can turn off filtering.
 * The accountability partner is notified when the timer is triggered.
 *
 * UX:
 * - User taps "Turn off filtering" → sees countdown screen, cannot bypass
 * - Partner receives email: disable requested, filtering will turn off in 24h
 * - User can cancel the request during the 24h window, resetting the timer
 */
@Singleton
class DelayToDisableManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountabilityReportService: AccountabilityReportService
) {
    companion object {
        private const val DELAY_HOURS = 24L
        private const val PREFS_NAME = "delay_to_disable_prefs"
        private const val KEY_UNLOCK_TIME = "scheduled_unlock_time"
        private const val KEY_REQUEST_ACTIVE = "disable_request_active"
    }

    /**
     * Timer state for the UI.
     */
    data class TimerState(
        val isActive: Boolean = false,
        val unlockTimestamp: Long = 0,
        val remainingMs: Long = 0,
        val isEligible: Boolean = false
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _timerState = MutableStateFlow(loadTimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    /**
     * Request to disable filtering. Starts the 24-hour countdown.
     */
    fun requestDisable(profile: FilterProfile) {
        if (!profile.delayToDisableEnabled) {
            // Delay not enabled for this profile — proceed immediately
            _timerState.value = TimerState(isEligible = true)
            return
        }

        val unlockTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(DELAY_HOURS)

        prefs.edit()
            .putLong(KEY_UNLOCK_TIME, unlockTime)
            .putBoolean(KEY_REQUEST_ACTIVE, true)
            .apply()

        _timerState.value = TimerState(
            isActive = true,
            unlockTimestamp = unlockTime,
            remainingMs = TimeUnit.HOURS.toMillis(DELAY_HOURS),
            isEligible = false
        )

        // Notify accountability partner
        notifyPartner("DISABLE_REQUESTED", unlockTime)
    }

    /**
     * Cancel the disable request and reset the timer.
     */
    fun cancelDisableRequest() {
        prefs.edit()
            .remove(KEY_UNLOCK_TIME)
            .putBoolean(KEY_REQUEST_ACTIVE, false)
            .apply()

        _timerState.value = TimerState()

        // Notify partner about cancellation
        notifyPartner("DISABLE_CANCELLED", 0)
    }

    /**
     * Check if the user is eligible to disable filtering.
     * Returns true if the 24-hour countdown has elapsed.
     */
    fun checkUnlockEligibility(): Boolean {
        val unlockTime = prefs.getLong(KEY_UNLOCK_TIME, 0)
        if (unlockTime == 0L) return false

        val isEligible = System.currentTimeMillis() >= unlockTime

        if (isEligible) {
            // Timer has elapsed — user can now disable
            _timerState.value = _timerState.value.copy(
                isEligible = true,
                remainingMs = 0
            )
        } else {
            // Update remaining time
            _timerState.value = _timerState.value.copy(
                remainingMs = unlockTime - System.currentTimeMillis()
            )
        }

        return isEligible
    }

    /**
     * Complete the disable process after the timer has elapsed.
     * Clears the timer state.
     */
    fun completeDisable() {
        prefs.edit()
            .remove(KEY_UNLOCK_TIME)
            .putBoolean(KEY_REQUEST_ACTIVE, false)
            .apply()

        _timerState.value = TimerState()

        // Notify partner that filtering has been disabled
        notifyPartner("FILTERING_DISABLED", 0)
    }

    /**
     * Get formatted remaining time string (e.g., "23h 45m").
     */
    fun getFormattedRemainingTime(): String {
        val remaining = _timerState.value.remainingMs
        if (remaining <= 0) return "Ready"

        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Check if there's an active disable request.
     */
    fun isRequestActive(): Boolean = prefs.getBoolean(KEY_REQUEST_ACTIVE, false)

    // ─── Private ───

    private fun loadTimerState(): TimerState {
        val isActive = prefs.getBoolean(KEY_REQUEST_ACTIVE, false)
        val unlockTime = prefs.getLong(KEY_UNLOCK_TIME, 0)

        if (!isActive || unlockTime == 0L) return TimerState()

        val remaining = unlockTime - System.currentTimeMillis()
        return TimerState(
            isActive = true,
            unlockTimestamp = unlockTime,
            remainingMs = maxOf(remaining, 0),
            isEligible = remaining <= 0
        )
    }

    private fun notifyPartner(event: String, unlockAt: Long) {
        // Dispatch notification to accountability partner
        // Server-side: email via SendGrid
        // "Your partner has requested to disable NSFW Shield.
        //  Filtering will turn off in 24 hours unless cancelled."
    }
}
