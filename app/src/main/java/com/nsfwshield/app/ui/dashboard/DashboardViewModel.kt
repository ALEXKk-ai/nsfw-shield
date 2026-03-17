package com.nsfwshield.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nsfwshield.app.core.ProtectionManager
import com.nsfwshield.app.core.logging.ActivityLogger
import com.nsfwshield.app.core.profiles.ProfileManager
import com.nsfwshield.app.data.BlockedDomainDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val blockedToday: Int = 0,
    val domainsFiltered: Int = 0,
    val activeProfileName: String = "—",
    val aiAccuracy: String = "—",
    val isProtectionActive: Boolean = true,
    val hasVpnPermission: Boolean = true,
    val hasAccessibilityPermission: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val activityLogger: ActivityLogger,
    private val profileManager: ProfileManager,
    private val blockedDomainDao: BlockedDomainDao,
    private val protectionManager: ProtectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
        observeActiveProfile()
        observeStatistics()
        observeProtectionState()
        observePermissions()
        
        viewModelScope.launch {
            protectionManager.syncServiceState()
        }
    }

    fun toggleProtection(active: Boolean) {
        viewModelScope.launch {
            val success = protectionManager.setProtectionActive(active)
            if (!success) {
                // In a production app, we would emit a 'ShowDelayNotification' event here
            }
        }
    }

    fun refreshStats() {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        // Initial static loads are handled by the flows, but we can do a quick snap
        // here if we want instant initial states. Our new reactive flows will override it instantly anyway,
        // so leaving this empty is safe, but we'll populate it statically just in case flows are delayed.
        viewModelScope.launch {
            val startOfDay = getStartOfDay()
            val blocked = activityLogger.getBlockedCount(startOfDay)
            val domains = blockedDomainDao.getTotalCount()
            _uiState.update {
                it.copy(
                    blockedToday = blocked,
                    domainsFiltered = domains
                )
            }
        }
    }

    private fun observeStatistics() {
        // Observe Blocked Today
        viewModelScope.launch {
            val startOfDay = getStartOfDay()
            activityLogger.getBlockedCountFlow(startOfDay).collect { count ->
                _uiState.update { it.copy(blockedToday = count) }
            }
        }
        
        // Observe Domains Filtered
        viewModelScope.launch {
            blockedDomainDao.getTotalCountFlow().collect { count ->
                _uiState.update { it.copy(domainsFiltered = count) }
            }
        }
    }

    private fun observeActiveProfile() {
        viewModelScope.launch {
            profileManager.getActiveProfileFlow().collect { activeProfile ->
                val accuracy = "${(activeProfile.confidenceThreshold * 100).toInt()}%"
                _uiState.update {
                    it.copy(
                        activeProfileName = activeProfile.displayName,
                        aiAccuracy = accuracy
                    )
                }
            }
        }
    }

    private fun observeProtectionState() {
        viewModelScope.launch {
            protectionManager.isProtectionActive.collect { active ->
                _uiState.update { it.copy(isProtectionActive = active) }
            }
        }
    }

    private fun observePermissions() {
        viewModelScope.launch {
            while (true) {
                _uiState.update {
                    it.copy(
                        hasVpnPermission = protectionManager.hasVpnPermission(),
                        hasAccessibilityPermission = protectionManager.hasAccessibilityPermission()
                    )
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun getStartOfDay(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
