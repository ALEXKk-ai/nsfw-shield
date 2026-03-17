package com.nsfwshield.app.ui.accountability

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nsfwshield.app.data.SettingsRepository
import com.nsfwshield.app.premium.AccountabilityReportService
import com.nsfwshield.app.reports.AccountabilityReportWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountabilityUiState(
    val partnerEmail: String = "",
    val partnerName: String = "",
    val reportFrequency: String = "Weekly",
    val isProtectionActive: Boolean = true,
    val disableRequestTime: Long? = null,
    val timeLeftToDisable: String? = null
)

@HiltViewModel
class AccountabilityViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val accountabilityReportService: AccountabilityReportService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountabilityUiState())
    val uiState: StateFlow<AccountabilityUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        startCountdownTimer()
    }

    fun updatePartnerInfo(email: String) {
        viewModelScope.launch {
            accountabilityReportService.setPartnerEmail(email)
        }
    }

    fun updateReportFrequency(frequency: AccountabilityReportService.ReportPeriod) {
        viewModelScope.launch {
            accountabilityReportService.setReportPeriod(frequency)
        }
    }

    fun setReportsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            accountabilityReportService.setEnabled(enabled)
        }
    }

    fun revokePartner() {
        viewModelScope.launch {
            accountabilityReportService.revokePartner()
        }
    }

    fun requestDisableProtection() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            settingsRepository.setDisableRequestTime(now)
        }
    }

    fun cancelDisableRequest() {
        viewModelScope.launch {
            settingsRepository.setDisableRequestTime(null)
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                accountabilityReportService.partnerConfig,
                settingsRepository.protectionActive,
                settingsRepository.disableRequestTime
            ) { config, active, requestTime ->
                // Return a pair of the UI state and the raw config for worker scheduling
                AccountabilityUiState(
                    partnerEmail = config.email,
                    reportFrequency = config.reportPeriod.name.lowercase().replaceFirstChar { it.uppercase() },
                    isProtectionActive = active,
                    disableRequestTime = requestTime
                ) to config
            }.collect { (state, config) ->
                _uiState.update { 
                    it.copy(
                        partnerEmail = state.partnerEmail,
                        reportFrequency = state.reportFrequency,
                        isProtectionActive = state.isProtectionActive,
                        disableRequestTime = state.disableRequestTime
                    )
                }

                // Schedule or cancel worker based on config
                if (config.isEnabled && config.email.isNotBlank() && config.isVerified) {
                    AccountabilityReportWorker.schedule(context, config.reportPeriod)
                } else {
                    AccountabilityReportWorker.cancel(context)
                }
            }
        }
    }

    private fun startCountdownTimer() {
        viewModelScope.launch {
            while (true) {
                val requestTime = _uiState.value.disableRequestTime
                if (requestTime != null) {
                    val waitMillis = 24 * 60 * 60 * 1000L // 24 hours
                    val elapsed = System.currentTimeMillis() - requestTime
                    val remaining = waitMillis - elapsed

                    if (remaining > 0) {
                        val hours = remaining / (1000 * 60 * 60)
                        val minutes = (remaining / (1000 * 60)) % 60
                        val seconds = (remaining / 1000) % 60
                        _uiState.update { 
                            it.copy(timeLeftToDisable = String.format("%02d:%02d:%02d", hours, minutes, seconds))
                        }
                    } else {
                        _uiState.update { it.copy(timeLeftToDisable = "Ready to Disable") }
                    }
                } else {
                    _uiState.update { it.copy(timeLeftToDisable = null) }
                }
                delay(1000)
            }
        }
    }
}
