package com.nsfwshield.app.data

import androidx.room.*
import com.nsfwshield.app.core.profiles.FilterProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM filter_profiles ORDER BY createdAt ASC")
    fun getAllProfiles(): Flow<List<FilterProfile>>

    @Query("""
        SELECT * FROM filter_profiles 
        WHERE ((isFamilyShield = 1 AND isSender = 0) OR (isFamilyShield = 0)) AND isActive = 1
        ORDER BY isFamilyShield DESC, updatedAt DESC LIMIT 1
    """)
    suspend fun getActiveProfile(): FilterProfile?

    @Query("""
        SELECT * FROM filter_profiles 
        WHERE ((isFamilyShield = 1 AND isSender = 0) OR (isFamilyShield = 0)) AND isActive = 1
        ORDER BY isFamilyShield DESC, updatedAt DESC LIMIT 1
    """)
    fun getActiveProfileFlow(): Flow<FilterProfile?>

    @Query("SELECT * FROM filter_profiles")
    suspend fun getAllProfilesSnapshot(): List<FilterProfile>

    @Query("SELECT * FROM filter_profiles WHERE profileId = :id")
    suspend fun getProfileById(id: String): FilterProfile?

    @Query("SELECT COUNT(*) FROM filter_profiles")
    suspend fun getProfileCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: FilterProfile)

    @Update
    suspend fun updateProfile(profile: FilterProfile)

    @Delete
    suspend fun deleteProfile(profile: FilterProfile)

    @Query("UPDATE filter_profiles SET isActive = 0 WHERE isFamilyShield = 0")
    suspend fun deactivateAllPersonalProfiles()

    @Query("UPDATE filter_profiles SET isActive = 0 WHERE isFamilyShield = 1")
    suspend fun deactivateAllManagedProfiles()

    @Query("UPDATE filter_profiles SET isActive = 1 WHERE profileId = :id")
    suspend fun activateProfile(id: String)

    @Transaction
    suspend fun setActiveProfile(id: String) {
        val target = getProfileById(id) ?: return
        if (target.isFamilyShield) {
            deactivateAllManagedProfiles()
        } else {
            deactivateAllPersonalProfiles()
        }
        activateProfile(id)
    }
}
