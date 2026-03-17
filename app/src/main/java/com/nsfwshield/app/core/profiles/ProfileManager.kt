package com.nsfwshield.app.core.profiles

import com.nsfwshield.app.data.ProfileDao
import com.nsfwshield.app.premium.SubscriptionGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages filter profiles — CRUD operations with subscription gating.
 * Multiple profiles require Premium. Free users get a single profile.
 */
@Singleton
class ProfileManager @Inject constructor(
    private val profileDao: ProfileDao
) {
    /**
     * Get all profiles as a Flow for reactive UI updates.
     */
    fun getAllProfiles(): Flow<List<FilterProfile>> = profileDao.getAllProfiles()

    /**
     * Get the currently active profile as a Flow.
     * Reactively updates whenever the active profile changes.
     */
    fun getActiveProfileFlow(): Flow<FilterProfile> = profileDao.getActiveProfileFlow().map { profile ->
        profile ?: createDefaultProfile()
    }

    /**
     * Get the currently active profile.
     */
    suspend fun getActiveProfile(): FilterProfile {
        // With strictly single-active, we just fetch the active one.
        // If nothing is active, create the default.
        return profileDao.getActiveProfile() ?: createDefaultProfile()
    }

    /**
     * Get a profile by its ID.
     */
    suspend fun getProfileById(id: String): FilterProfile? = profileDao.getProfileById(id)

    /**
     * Create a new profile. Returns false if free user already has a profile.
     */
    suspend fun createProfile(profile: FilterProfile, isPremium: Boolean): Result<FilterProfile> {
        val currentCount = profileDao.getProfileCount()

        val personalCount = profileDao.getAllProfilesSnapshot().count { !it.isFamilyShield }

        if (!profile.isFamilyShield && personalCount >= 1) {
            return Result.failure(
                IllegalStateException("You can only have one Personal Profile. Use Managed Shields to protect others.")
            )
        }

        if (!isPremium && profile.isFamilyShield && (currentCount - personalCount) >= 1) {
            return Result.failure(
                IllegalStateException("Multiple Managed Shields require Premium subscription")
            )
        }

        // If this is a personal profile, it MUST be active (baseline)
        val finalProfile = if (!profile.isFamilyShield) {
            profile.copy(isActive = true)
        } else if (currentCount == 0) {
            profile.copy(isActive = true)
        } else {
            profile
        }

        profileDao.insertProfile(finalProfile)
        return Result.success(finalProfile)
    }

    /**
     * Update an existing profile.
     */
    suspend fun updateProfile(profile: FilterProfile) {
        profileDao.updateProfile(
            profile.copy(updatedAt = System.currentTimeMillis())
        )
    }

    /**
     * Delete a profile. Cannot delete the last remaining profile.
     */
    suspend fun deleteProfile(profile: FilterProfile): Result<Unit> {
        if (!profile.isFamilyShield) {
            return Result.failure(
                IllegalStateException("Your Personal Profile cannot be deleted. It is your default protection.")
            )
        }

        profileDao.deleteProfile(profile)
        // No need to manually activate anything else: 
        // the baseline Personal Profile is already isActive = 1 in the background!

        return Result.success(Unit)
    }

    /**
     * Set a profile as the active one (deactivates others of SAME type).
     */
    suspend fun setActiveProfile(profileId: String) {
        profileDao.setActiveProfile(profileId)
    }

    /**
     * Get the number of profiles.
     */
    suspend fun getProfileCount(): Int = profileDao.getProfileCount()

    // ─── Private helpers ───

    private suspend fun createDefaultProfile(): FilterProfile {
        val existingPersonal = profileDao.getAllProfilesSnapshot().find { !it.isFamilyShield }
        if (existingPersonal != null) {
            profileDao.activateProfile(existingPersonal.profileId)
            return existingPersonal.copy(isActive = true)
        }
        val default = FilterProfile.createModerate().copy(isActive = true)
        profileDao.insertProfile(default)
        return default
    }
}
