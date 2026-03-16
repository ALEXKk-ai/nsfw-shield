package com.nsfwshield.app.ui.main

import androidx.lifecycle.ViewModel
import com.nsfwshield.app.core.ProtectionManager
import com.nsfwshield.app.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

/**
 * Global ViewModel for MainActivity to handle app-wide state (Theme, etc).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val protectionManager: ProtectionManager
) : ViewModel() {

    /**
     * Observable dark mode state.
     */
    val darkMode: Flow<Boolean> = settingsRepository.darkMode

    /**
     * Observable onboarding status.
     */
    val onboardingCompleted: Flow<Boolean> = settingsRepository.onboardingCompleted

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(completed)
        }
    }

    /**
     * Called when the user explicitly accepts protection during onboarding.
     */
    fun startProtection() {
        viewModelScope.launch {
            protectionManager.setProtectionActive(true)
        }
    }
}
