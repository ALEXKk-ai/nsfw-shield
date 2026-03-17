package com.nsfwshield.app.core

import android.content.Context
import android.content.Intent
import com.nsfwshield.app.data.SettingsRepository
import com.nsfwshield.app.monitoring.NSFWAccessibilityService
import com.nsfwshield.app.network.LocalVpnService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the master protection toggle with VPN and Accessibility services.
 * Reads actual running status and provides controls to start/stop them.
 */
@Singleton
class ProtectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    /**
     * The persisted protection state from DataStore.
     */
    val isProtectionActive: Flow<Boolean> = settingsRepository.protectionActive

    /**
     * Check if services are actually running right now.
     */
    fun isVpnRunning(): Boolean {
        return try {
            LocalVpnService.isRunning
        } catch (e: Exception) {
            false
        }
    }

    fun isAccessibilityRunning(): Boolean {
        return NSFWAccessibilityService.isRunning
    }

    /**
     * Check system-level permissions.
     */
    fun hasVpnPermission(): Boolean {
        return try {
            android.net.VpnService.prepare(context) == null
        } catch (e: Exception) {
            false
        }
    }

    fun hasAccessibilityPermission(): Boolean {
        return com.nsfwshield.app.core.PermissionUtils.isAccessibilityServiceEnabled(context) &&
               android.provider.Settings.canDrawOverlays(context)
    }

    /**
     * Cross-references saved settings with actual running state and permissions.
     * Fires on app startup to ensure services are running if they are supposed to be.
     */
    suspend fun syncServiceState() {
        val isActive = settingsRepository.protectionActive.firstOrNull() ?: true
        if (isActive && hasVpnPermission() && !isVpnRunning()) {
            startVpnService()
        }
    }

    /**
     * Toggle protection on/off. Starts or stops the VPN service
     * and persists the user's preference.
     * Returns true if the action was performed, false if blocked by delay timer.
     */
    suspend fun setProtectionActive(active: Boolean): Boolean {
        if (!active) {
            val requestTime = settingsRepository.disableRequestTime.firstOrNull()
            if (requestTime != null) {
                val waitMillis = 24 * 60 * 60 * 1000L
                val elapsed = System.currentTimeMillis() - requestTime
                if (elapsed < waitMillis) {
                    return false // Still in countdown
                }
            } else {
                // Check if accountability partner is set, if so, we can't disable without request
                val partnerEmail = settingsRepository.partnerEmail.firstOrNull()
                if (!partnerEmail.isNullOrBlank()) {
                    // In a production app, we would force them to the Accountability screen
                    // For now, if no request is set, we block immediate disable
                    return false
                }
            }
        }

        settingsRepository.setProtectionActive(active)
        if (active) {
            // When enabling, always clear any pending disable request
            settingsRepository.setDisableRequestTime(null)
            startVpnService()
        } else {
            stopVpnService()
        }
        return true
    }

    private fun startVpnService() {
        try {
            val intent = Intent(context, LocalVpnService::class.java).apply {
                action = LocalVpnService.ACTION_START
            }
            context.startService(intent)
        } catch (e: Exception) {
            // VPN permission may not be granted yet
        }
    }

    private fun stopVpnService() {
        try {
            val intent = Intent(context, LocalVpnService::class.java).apply {
                action = LocalVpnService.ACTION_STOP
            }
            context.startService(intent)
        } catch (e: Exception) {
            // Service may not be running
        }
    }
}
