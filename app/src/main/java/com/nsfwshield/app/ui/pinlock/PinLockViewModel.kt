package com.nsfwshield.app.ui.pinlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nsfwshield.app.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinLockUiState(
    val isPinSet: Boolean = false,
    val isChecking: Boolean = true,
    val isSuccess: Boolean = false,
    val uninstallProtectionEnabled: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PinLockViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinLockUiState())
    val uiState: StateFlow<PinLockUiState> = _uiState.asStateFlow()

    init {
        checkPinStatus()
    }

    private fun checkPinStatus() {
        viewModelScope.launch {
            combine(
                settingsRepository.adminPin,
                settingsRepository.uninstallProtectionEnabled
            ) { pin, protection ->
                PinLockUiState(
                    isPinSet = !pin.isNullOrBlank(),
                    isChecking = false,
                    uninstallProtectionEnabled = protection
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            settingsRepository.setAdminPin(pin)
            _uiState.value = _uiState.value.copy(isPinSet = true, isSuccess = true, error = null)
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            val savedPin = settingsRepository.adminPin.first()
            if (savedPin == pin) {
                _uiState.value = _uiState.value.copy(isSuccess = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(isSuccess = false, error = "Incorrect PIN. Try again.")
            }
        }
    }

    fun removePin() {
        viewModelScope.launch {
            if (_uiState.value.uninstallProtectionEnabled) {
                _uiState.value = _uiState.value.copy(error = "Disable Uninstall Protection before removing PIN.")
                return@launch
            }
            settingsRepository.setAdminPin(null)
            _uiState.value = _uiState.value.copy(isPinSet = false, isSuccess = true, error = null)
        }
    }
    
    fun resetSuccessState() {
        _uiState.value = _uiState.value.copy(isSuccess = false)
    }
}
