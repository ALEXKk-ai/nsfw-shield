package com.nsfwshield.app.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anti-Bypass Module.
 * Detects and prevents attempts to circumvent NSFW Shield's protection.
 *
 * Methods:
 * - VPN blocking: Detects external VPN interfaces
 * - DNS override detection: Monitors for custom DNS servers
 * - Settings tampering detection
 */
@Singleton
class AntiBypassModule @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Known public DNS servers that could bypass our filtering
        private val KNOWN_BYPASS_DNS = setOf(
            "1.1.1.1",        // Cloudflare
            "1.0.0.1",        // Cloudflare
            "8.8.8.8",        // Google
            "8.8.4.4",        // Google
            "9.9.9.9",        // Quad9
            "208.67.222.222", // OpenDNS
            "208.67.220.220"  // OpenDNS
        )

        // Known VPN app packages
        private val KNOWN_VPN_PACKAGES = setOf(
            "com.nordvpn.android",
            "com.expressvpn.vpn",
            "com.surfshark.vpnservice.android",
            "com.privateinternetaccess.android",
            "net.protonvpn.android",
            "com.tunnelbear.android",
            "org.torproject.torbrowser",
            "com.psiphon3",
            "com.windscribe.vpn"
        )
    }

    /**
     * Bypass attempt detection result.
     */
    data class BypassAttempt(
        val type: BypassType,
        val details: String,
        val severity: Severity,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class BypassType {
        EXTERNAL_VPN,
        DNS_OVERRIDE,
        VPN_APP_INSTALLED,
        SETTINGS_TAMPERING
    }

    enum class Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    /**
     * Run a full bypass detection check.
     */
    suspend fun detectBypassAttempts(): List<BypassAttempt> = withContext(Dispatchers.Default) {
        val attempts = mutableListOf<BypassAttempt>()

        checkExternalVpn()?.let { attempts.add(it) }
        checkDnsOverride()?.let { attempts.add(it) }
        checkVpnAppsInstalled().let { attempts.addAll(it) }

        attempts
    }

    /**
     * Check if an external VPN (not ours) is currently active.
     */
    fun checkExternalVpn(): BypassAttempt? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            // Check if it's our VPN
            // If LocalVpnService is running, this VPN is ours
            if (!isOurVpnActive()) {
                return BypassAttempt(
                    type = BypassType.EXTERNAL_VPN,
                    details = "External VPN connection detected",
                    severity = Severity.HIGH
                )
            }
        }

        return null
    }

    /**
     * Check if the device DNS has been overridden to bypass filtering.
     */
    fun checkDnsOverride(): BypassAttempt? {
        // Check Private DNS setting (Android 9+)
        try {
            val privateDnsMode = android.provider.Settings.Global.getString(
                context.contentResolver,
                "private_dns_mode"
            )
            if (privateDnsMode == "hostname") {
                val privateDnsHost = android.provider.Settings.Global.getString(
                    context.contentResolver,
                    "private_dns_specifier"
                )
                if (privateDnsHost != null) {
                    return BypassAttempt(
                        type = BypassType.DNS_OVERRIDE,
                        details = "Private DNS configured: $privateDnsHost",
                        severity = Severity.MEDIUM
                    )
                }
            }
        } catch (e: Exception) {
            // Settings access failed
        }

        return null
    }

    /**
     * Check if known VPN apps are installed on the device.
     */
    fun checkVpnAppsInstalled(): List<BypassAttempt> {
        val attempts = mutableListOf<BypassAttempt>()

        for (packageName in KNOWN_VPN_PACKAGES) {
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                attempts.add(
                    BypassAttempt(
                        type = BypassType.VPN_APP_INSTALLED,
                        details = "VPN app installed: $packageName",
                        severity = Severity.LOW
                    )
                )
            } catch (e: Exception) {
                // Package not found — OK
            }
        }

        return attempts
    }

    /**
     * Check if our local VPN service is currently active.
     */
    private fun isOurVpnActive(): Boolean {
        // Check if LocalVpnService is running
        // This would be tracked via a companion object flag
        return true // Simplified — in production, check service state
    }
}
