package com.nsfwshield.app.premium

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accountability Report Service — Premium feature.
 *
 * Builds metadata-only reports (blocked count, categories, override events)
 * and dispatches them to the accountability partner via server-side SendGrid API.
 *
 * PRIVACY: Reports contain aggregated metadata ONLY — never URLs, content,
 * or screenshots.
 */
@Singleton
class AccountabilityReportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Report frequency options.
     */
    enum class ReportPeriod {
        DAILY,
        WEEKLY
    }

    /**
     * Accountability report data — metadata only.
     */
    data class AccountabilityReport(
        val period: ReportPeriod,
        val blockedAttempts: Int,
        val topBlockedCategories: List<String>,
        val overrideEvents: Int,
        val delayTimerTriggers: Int,
        val partnerEmail: String,
        val generatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Partner configuration state.
     */
    data class PartnerConfig(
        val email: String = "",
        val isVerified: Boolean = false,
        val reportPeriod: ReportPeriod = ReportPeriod.WEEKLY,
        val isEnabled: Boolean = false
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "accountability_prefs", Context.MODE_PRIVATE
    )

    private val _partnerConfig = MutableStateFlow(loadPartnerConfig())
    val partnerConfig: StateFlow<PartnerConfig> = _partnerConfig.asStateFlow()

    /**
     * Set up accountability partner email.
     * Sends a verification email before reports begin.
     */
    fun setPartnerEmail(email: String) {
        prefs.edit()
            .putString("partner_email", email)
            .putBoolean("partner_verified", false)
            .apply()

        _partnerConfig.value = _partnerConfig.value.copy(
            email = email,
            isVerified = false
        )

        // In production: Send verification email via backend API
        sendVerificationEmail(email)
    }

    /**
     * Confirm partner email verification.
     */
    fun confirmPartnerVerification() {
        prefs.edit().putBoolean("partner_verified", true).apply()
        _partnerConfig.value = _partnerConfig.value.copy(isVerified = true)
    }

    /**
     * Set the report delivery frequency.
     */
    fun setReportPeriod(period: ReportPeriod) {
        prefs.edit().putString("report_period", period.name).apply()
        _partnerConfig.value = _partnerConfig.value.copy(reportPeriod = period)
    }

    /**
     * Enable or disable accountability reports.
     */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("accountability_enabled", enabled).apply()
        _partnerConfig.value = _partnerConfig.value.copy(isEnabled = enabled)
    }

    /**
     * Revoke the accountability partner.
     * Note: The revocation itself is reported to the partner.
     */
    fun revokePartner() {
        val currentEmail = _partnerConfig.value.email
        if (currentEmail.isNotBlank()) {
            // Notify partner about revocation
            sendRevocationNotice(currentEmail)
        }

        prefs.edit()
            .remove("partner_email")
            .remove("partner_verified")
            .putBoolean("accountability_enabled", false)
            .apply()

        _partnerConfig.value = PartnerConfig()
    }

    /**
     * Generate and send the accountability report.
     * Called by a scheduled worker based on the report period.
     */
    suspend fun generateAndSendReport(
        blockedAttempts: Int,
        topCategories: List<String>,
        overrideEvents: Int,
        delayTimerTriggers: Int
    ) {
        val config = _partnerConfig.value
        if (!config.isEnabled || !config.isVerified || config.email.isBlank()) return

        val report = AccountabilityReport(
            period = config.reportPeriod,
            blockedAttempts = blockedAttempts,
            topBlockedCategories = topCategories,
            overrideEvents = overrideEvents,
            delayTimerTriggers = delayTimerTriggers,
            partnerEmail = config.email
        )

        dispatchReport(report)
    }

    // ─── Private ───

    private fun loadPartnerConfig(): PartnerConfig {
        return PartnerConfig(
            email = prefs.getString("partner_email", "") ?: "",
            isVerified = prefs.getBoolean("partner_verified", false),
            reportPeriod = try {
                ReportPeriod.valueOf(prefs.getString("report_period", "WEEKLY") ?: "WEEKLY")
            } catch (e: Exception) {
                ReportPeriod.WEEKLY
            },
            isEnabled = prefs.getBoolean("accountability_enabled", false)
        )
    }

    private fun sendVerificationEmail(email: String) {
        // Server-side: POST to backend → SendGrid API
        // Sends a confirmation link to the partner's email
    }

    private fun sendRevocationNotice(email: String) {
        // Server-side: Notify partner that accountability has been revoked
    }

    private suspend fun dispatchReport(report: AccountabilityReport) {
        // Server-side only — SendGrid API call from backend
        // POST report metadata to backend endpoint
        // Backend formats and sends email via SendGrid
        // Never sends raw content, only aggregated stats
    }
}
