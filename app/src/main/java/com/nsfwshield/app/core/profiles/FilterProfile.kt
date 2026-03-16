package com.nsfwshield.app.core.profiles

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Filter sensitivity level preset.
 */
enum class SensitivityLevel {
    STRICT,     // Low threshold, broad blocking (children's devices)
    MODERATE,   // Balanced (default for general use)
    CUSTOM      // User-defined per-category with manual threshold (Premium)
}

/**
 * Content categories that can be individually toggled.
 */
data class CategoryToggles(
    val explicitSexual: Boolean = true,
    val violenceGore: Boolean = true,
    val hateSpeech: Boolean = true,
    val drugReferences: Boolean = true,
    val gambling: Boolean = true,
    val selfHarm: Boolean = true
)

/**
 * A named filter profile with sensitivity settings.
 * Each user/device can have one or more profiles (multiple = Premium).
 */
@Entity(tableName = "filter_profiles")
data class FilterProfile(
    @PrimaryKey
    val profileId: String = UUID.randomUUID().toString(),
    val displayName: String = "Default",
    val sensitivity: SensitivityLevel = SensitivityLevel.MODERATE,
    val confidenceThreshold: Float = 0.80f,
    val explicitSexual: Boolean = true,
    val violenceGore: Boolean = true,
    val hateSpeech: Boolean = true,
    val drugReferences: Boolean = true,
    val gambling: Boolean = true,
    val selfHarm: Boolean = true,
    val allowOverride: Boolean = false,
    val overridePinRequired: Boolean = true,
    val delayToDisableEnabled: Boolean = false,
    val isActive: Boolean = true,
    val isFamilyShield: Boolean = false,
    val isSender: Boolean = false, // True if created on this device to manage another
    val pairingSecret: String = UUID.randomUUID().toString(), // Used for deletion handshake
    val isLinked: Boolean = false, // True if shared (Parent) or imported (Child)
    val isPremiumRequired: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns category toggles as a structured object.
     */
    fun getCategoryToggles() = CategoryToggles(
        explicitSexual = explicitSexual,
        violenceGore = violenceGore,
        hateSpeech = hateSpeech,
        drugReferences = drugReferences,
        gambling = gambling,
        selfHarm = selfHarm
    )

    companion object {
        fun createChildSafe() = FilterProfile(
            displayName = "Child Safe",
            sensitivity = SensitivityLevel.STRICT,
            confidenceThreshold = 0.70f,
            allowOverride = false,
            overridePinRequired = true
        )

        fun createModerate() = FilterProfile(
            displayName = "Moderate",
            sensitivity = SensitivityLevel.MODERATE,
            confidenceThreshold = 0.80f,
            allowOverride = true
        )

        fun createCustom() = FilterProfile(
            displayName = "Custom",
            sensitivity = SensitivityLevel.CUSTOM,
            confidenceThreshold = 0.85f,
            isPremiumRequired = true
        )

        /**
         * Serializes a profile into a compact alphanumeric "Beam Code".
         * Version 3 includes custom blocked domains and pairingSecret.
         * Format: V3|Name|Sensitivity|Threshold|Sexual|Violence|Hate|Drugs|Gambling|Harm|Delay|isShield|PairingSecret|Domain1,Domain2...
         */
        fun FilterProfile.toBeamCode(customDomains: List<String> = emptyList()): String {
            val domainsString = customDomains.joinToString(",")
            return listOf(
                "V3",
                displayName,
                sensitivity.name,
                "%.2f".format(confidenceThreshold),
                if (explicitSexual) "1" else "0",
                if (violenceGore) "1" else "0",
                if (hateSpeech) "1" else "0",
                if (drugReferences) "1" else "0",
                if (gambling) "1" else "0",
                if (selfHarm) "1" else "0",
                if (delayToDisableEnabled) "1" else "0",
                if (isFamilyShield) "1" else "0",
                if (isLinked) "1" else "0",
                pairingSecret,
                domainsString
            ).map { it.replace("|", "_") }.joinToString("|")
        }

        /**
         * Reconstructs a profile and its domains from a Beam Code string.
         * Returns a Pair of [FilterProfile] and the list of custom domains.
         */
        fun fromBeamCode(code: String): Pair<FilterProfile, List<String>>? {
            return try {
                val parts = code.split("|")
                if (parts.size < 11) return null // Support older V1/V2 codes
                
                val version = parts[0]
                val profile = FilterProfile(
                    displayName = parts[1],
                    sensitivity = SensitivityLevel.valueOf(parts[2]),
                    confidenceThreshold = parts[3].toFloat(),
                    explicitSexual = parts[4] == "1",
                    violenceGore = parts[5] == "1",
                    hateSpeech = parts[6] == "1",
                    drugReferences = parts[7] == "1",
                    gambling = parts[8] == "1",
                    selfHarm = parts[9] == "1",
                    delayToDisableEnabled = parts[10] == "1",
                    isFamilyShield = if (parts.size >= 12) parts[11] == "1" else false,
                    isLinked = if (parts.size >= 13) parts[12] == "1" else false,
                    pairingSecret = if (version == "V3" && parts.size >= 14) parts[13] else UUID.randomUUID().toString(),
                    isSender = false // Received profiles are recipients by default
                )

                val domains = when (version) {
                    "V3" -> if (parts.size >= 15 && parts[14].isNotBlank()) parts[14].split(",") else emptyList()
                    "V2" -> if (parts.size >= 13 && parts[12].isNotBlank()) parts[12].split(",") else emptyList()
                    else -> emptyList()
                }

                Pair(profile, domains)
            } catch (e: Exception) {
                null
            }
        }
    }
}
