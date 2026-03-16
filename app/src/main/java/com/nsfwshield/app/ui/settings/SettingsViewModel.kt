package com.nsfwshield.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nsfwshield.app.core.ProtectionManager
import com.nsfwshield.app.data.SettingsRepository
import com.nsfwshield.app.network.BlocklistUpdateScheduler
import com.nsfwshield.app.security.UninstallProtection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val vpnEnabled: Boolean = true,
    val accessibilityEnabled: Boolean = true,
    val safeSearchEnabled: Boolean = true,
    val mediaScanEnabled: Boolean = false,
    val darkMode: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val uninstallProtectionEnabled: Boolean = false,
    val isPinSet: Boolean = false,
    val autoUpdateBlocklist: Boolean = true,
    val updateOnlyOnWifi: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val protectionManager: ProtectionManager,
    private val uninstallProtection: UninstallProtection,
    private val blocklistUpdateScheduler: BlocklistUpdateScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    fun setVpnEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                val success = protectionManager.setProtectionActive(false)
                if (success) {
                    settingsRepository.setVpnEnabled(false)
                }
            } else {
                settingsRepository.setVpnEnabled(true)
                protectionManager.setProtectionActive(true)
            }
        }
    }

    fun setAccessibilityEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAccessibilityEnabled(enabled)
        }
    }

    fun setSafeSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSafeSearchEnabled(enabled)
        }
    }

    fun setMediaScanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMediaScanEnabled(enabled)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkMode(enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
        }
    }

    fun setUninstallProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                uninstallProtection.enableProtection()
                settingsRepository.setUninstallProtectionEnabled(true)
            } else {
                uninstallProtection.disableProtection()
                uninstallProtection.removeAdmin()
                settingsRepository.setUninstallProtectionEnabled(false)
            }
        }
    }

    fun setAutoUpdateBlocklist(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoUpdateBlocklist(enabled)
            if (enabled) {
                val wifiOnly = settingsRepository.updateOnlyOnWifi.first()
                blocklistUpdateScheduler.schedule(wifiOnly)
            } else {
                blocklistUpdateScheduler.cancel()
            }
        }
    }

    fun setUpdateOnlyOnWifi(wifiOnly: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUpdateOnlyOnWifi(wifiOnly)
            val autoUpdate = settingsRepository.autoUpdateBlocklist.first()
            if (autoUpdate) {
                blocklistUpdateScheduler.schedule(wifiOnly)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.vpnEnabled,
                settingsRepository.accessibilityEnabled,
                settingsRepository.safeSearchEnabled,
                settingsRepository.mediaScanEnabled,
                settingsRepository.darkMode,
                settingsRepository.notificationsEnabled,
                settingsRepository.uninstallProtectionEnabled,
                settingsRepository.adminPin,
                settingsRepository.autoUpdateBlocklist,
                settingsRepository.updateOnlyOnWifi
            ) { values ->
                SettingsUiState(
                    vpnEnabled = values[0] as Boolean,
                    accessibilityEnabled = values[1] as Boolean,
                    safeSearchEnabled = values[2] as Boolean,
                    mediaScanEnabled = values[3] as Boolean,
                    darkMode = values[4] as Boolean,
                    notificationsEnabled = values[5] as Boolean,
                    uninstallProtectionEnabled = values[6] as Boolean,
                    isPinSet = (values[7] as String?)?.isNotEmpty() == true,
                    autoUpdateBlocklist = values[8] as Boolean,
                    updateOnlyOnWifi = values[9] as Boolean
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
