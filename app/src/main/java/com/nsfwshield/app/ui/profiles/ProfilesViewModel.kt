package com.nsfwshield.app.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nsfwshield.app.core.profiles.FilterProfile
import com.nsfwshield.app.core.profiles.ProfileManager
import com.nsfwshield.app.core.profiles.SensitivityLevel
import com.nsfwshield.app.data.BlockedDomain
import com.nsfwshield.app.network.DomainBlocklist
import com.nsfwshield.app.premium.SubscriptionGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfilesUiState(
    val personalProfiles: List<FilterProfile> = emptyList(),
    val managedShields: List<FilterProfile> = emptyList(), // Parents (Senders)
    val managedStrategies: List<FilterProfile> = emptyList(), // Children (Recipients)
    val customDomains: List<BlockedDomain> = emptyList(),
    val isPremium: Boolean = false,
    val errorMessage: String? = null,
    val pendingHandshakeProfile: FilterProfile? = null // Profile waiting for deletion QR
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileManager: ProfileManager,
    private val subscriptionGate: SubscriptionGate,
    private val domainBlocklist: DomainBlocklist,
    private val adminLockManager: com.nsfwshield.app.security.AdminLockManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    // Expose custom domains for the share dialog
    val customDomains: StateFlow<List<BlockedDomain>> = _uiState
        .map { it.customDomains }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeProfiles()
        observeSubscription()
        observeCustomDomains()
    }

    fun activateProfile(profileId: String) {
        viewModelScope.launch {
            profileManager.setActiveProfile(profileId)
        }
    }

    fun initiateDeleteProfile(profile: FilterProfile) {
        viewModelScope.launch {
            // Handshake Logic:
            // If it's a Managed Shield that has been LINKED/SHARED, we require a Handshake.
            if (profile.isFamilyShield && profile.isLinked) {
                _uiState.update { it.copy(pendingHandshakeProfile = profile, errorMessage = null) }
                return@launch
            }

            val result = profileManager.deleteProfile(profile)
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message) }
            }
        }
    }

    fun markProfileAsLinked(profileId: String) {
        viewModelScope.launch {
            val profile = _uiState.value.managedShields.find { it.profileId == profileId } ?: return@launch
            if (!profile.isLinked) {
                profileManager.updateProfile(profile.copy(isLinked = true))
            }
        }
    }

    fun cancelHandshakeDeletion() {
        _uiState.update { it.copy(pendingHandshakeProfile = null) }
    }

    /**
     * Completes deletion if the scanned code matches the pairingSecret.
     * Code format for deletion: ACTION|PROFILE_ID|SECRET
     */
    fun completeHandshakeDeletion(scannedCode: String) {
        val profile = _uiState.value.pendingHandshakeProfile ?: return
        
        viewModelScope.launch {
            val parts = scannedCode.split("|")
            
            // 1. Basic format check
            if (parts.size < 3 || parts[0] != "DELETE_ACTION") {
                _uiState.update { it.copy(errorMessage = "Invalid format: This code is not a valid Handshake key.") }
                return@launch
            }

            // 2. Name check (ensure key belongs to this profile name)
            if (parts[1] != profile.displayName) {
                _uiState.update { it.copy(errorMessage = "Profile mismatch: This key is for '${parts[1]}', but you are deleting '${profile.displayName}'.") }
                return@launch
            }

            // 3. Secret verify
            val isSuccess = parts[2] == profile.pairingSecret
            
            if (isSuccess) {
                val result = profileManager.deleteProfile(profile)
                result.onSuccess {
                    _uiState.update { it.copy(pendingHandshakeProfile = null, errorMessage = "Profile '${profile.displayName}' terminated successfully.") }
                }.onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message, pendingHandshakeProfile = null) }
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Handshake Failed: Incorrect security secret. Verify the code on the other device.") }
            }
        }
    }

    /**
     * Generates a code that another device can scan to AUTHORIZE deletion of this profile.
     */
    fun getDeletionAuthorizationCode(profile: FilterProfile): String {
        return "DELETE_ACTION|${profile.displayName}|${profile.pairingSecret}"
    }

    fun createProfile(name: String, sensitivity: SensitivityLevel, isFamilyShield: Boolean = false) {
        viewModelScope.launch {
            val newProfile = FilterProfile(
                displayName = name,
                sensitivity = sensitivity,
                isFamilyShield = isFamilyShield,
                isSender = isFamilyShield, // It's a sender if it's a family shield created here
                isLinked = false, // Newly created local shields are not linked yet
                isActive = false, 
                confidenceThreshold = when (sensitivity) {
                    SensitivityLevel.STRICT -> 0.70f
                    SensitivityLevel.MODERATE -> 0.80f
                    SensitivityLevel.CUSTOM -> 0.85f
                }
            )
            val result = profileManager.createProfile(newProfile, _uiState.value.isPremium)
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message) }
            }
        }
    }

    fun updateProfile(profile: FilterProfile) {
        viewModelScope.launch {
            profileManager.updateProfile(profile)
        }
    }

    fun addCustomDomain(domain: String) {
        if (!_uiState.value.isPremium) {
            _uiState.update { it.copy(errorMessage = "Custom blocklists require Premium") }
            return
        }
        viewModelScope.launch {
            domainBlocklist.addCustomDomain(domain)
        }
    }

    fun removeCustomDomain(domain: String) {
        viewModelScope.launch {
            domainBlocklist.removeCustomDomain(domain)
        }
    }

    fun importProfileFromBeamCode(code: String) {
        viewModelScope.launch {
            val resultPair = FilterProfile.fromBeamCode(code)
            if (resultPair != null) {
                val (profile, domains) = resultPair
                
            // Strict Single Managed Strategy: Any existing strategy is replaced by the new scan
            val existingStrategy = uiState.value.managedStrategies.firstOrNull()
            
            // Hybrid Model: Imported profiles are always Family Shields, Active, and LINKED
            val importedProfile = profile.copy(
                isFamilyShield = true,
                isSender = false, // Received profiles are always recipients
                isLinked = true,  // IMPORTED = LINKED/ESTABLISHED
                isActive = true
            )

            if (existingStrategy != null) {
                // Anti-Hijacking Check: 
                // If the new scan has a different pairingSecret than the one we already have,
                // we REQUIRE a handshake to "Release" the old guardian first.
                if (importedProfile.pairingSecret != existingStrategy.pairingSecret) {
                    _uiState.update { 
                        it.copy(
                            pendingHandshakeProfile = existingStrategy,
                            errorMessage = "Security Lock: This device is already managed by '${existingStrategy.displayName}'. " +
                                           "Release this shield via Handshake before applying '${profile.displayName}'."
                        ) 
                    }
                    return@launch
                }

                // It's an update from the SAME guardian! Overwrite everything but keep the local ID
                profileManager.updateProfile(importedProfile.copy(profileId = existingStrategy.profileId))
                _uiState.update { it.copy(errorMessage = "Managed Shield '${profile.displayName}' updated successfully.") }
                return@launch
            }
                
                // Premium Guard: Free users only get 1 profile total (including family shields)
                val totalCount = _uiState.value.personalProfiles.size + 
                                 _uiState.value.managedShields.size + 
                                 _uiState.value.managedStrategies.size
                if (!_uiState.value.isPremium && totalCount >= 1) {
                    _uiState.update { it.copy(errorMessage = "Import Failed: Multiple profiles require Premium.") }
                    return@launch
                }

                // Create the profile
                val profileResult = profileManager.createProfile(
                    importedProfile,
                    _uiState.value.isPremium
                )
                
                profileResult.onSuccess {
                    // Activate it (will deactivate other family shields)
                    profileManager.setActiveProfile(it.profileId)
                    
                    // Also import the domains (only if premium)
                    if (_uiState.value.isPremium) {
                        domains.forEach { domain ->
                            domainBlocklist.addCustomDomain(domain)
                        }
                    }
                    _uiState.update { it.copy(errorMessage = "Shield '${profile.displayName}' linked and active.") }
                }.onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Invalid Beam Code: Please ensure the other device is showing a valid QR.") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun verifyPin(pin: String): Boolean {
        return adminLockManager.verifyPin(pin)
    }

    fun isPinSet(): Boolean = adminLockManager.isPinSet()

    private fun observeProfiles() {
        viewModelScope.launch {
            profileManager.getAllProfiles().collect { profiles ->
                _uiState.update { state ->
                    state.copy(
                        personalProfiles = profiles.filter { !it.isFamilyShield },
                        managedShields = profiles.filter { it.isFamilyShield && it.isSender },
                        managedStrategies = profiles.filter { it.isFamilyShield && !it.isSender }
                    )
                }
            }
        }
    }

    private fun observeSubscription() {
        viewModelScope.launch {
            subscriptionGate.subscriptionState.collect { sub ->
                _uiState.update { it.copy(isPremium = sub.isPremium) }
            }
        }
    }

    private fun observeCustomDomains() {
        viewModelScope.launch {
            domainBlocklist.getCustomDomains().collect { domains: List<BlockedDomain> ->
                _uiState.update { it.copy(customDomains = domains) }
            }
        }
    }
}
