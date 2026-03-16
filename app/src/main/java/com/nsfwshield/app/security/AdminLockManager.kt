package com.nsfwshield.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Admin Lock Manager.
 * Provides PIN-based access control for administrative functions
 * (changing settings, overriding blocks, uninstalling).
 */
@Singleton
class AdminLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "admin_lock_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SET = "pin_is_set"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 300_000L // 5 minutes
    }

    /**
     * Check if a PIN has been set.
     */
    fun isPinSet(): Boolean = prefs.getBoolean(KEY_PIN_SET, false)

    /**
     * Set a new admin PIN.
     */
    fun setPin(pin: String): Boolean {
        if (pin.length < 4 || pin.length > 8) return false

        prefs.edit()
            .putString(KEY_PIN_HASH, hashPin(pin))
            .putBoolean(KEY_PIN_SET, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply()

        return true
    }

    /**
     * Verify a PIN. Returns true if correct.
     * Implements lockout after MAX_FAILED_ATTEMPTS.
     */
    fun verifyPin(pin: String): Boolean {
        if (isLockedOut()) return false

        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val inputHash = hashPin(pin)

        return if (storedHash == inputHash) {
            // Reset failed attempts on success
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply()
            true
        } else {
            // Increment failed attempts
            val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                prefs.edit().putLong(
                    KEY_LOCKOUT_UNTIL,
                    System.currentTimeMillis() + LOCKOUT_DURATION_MS
                ).apply()
            }
            false
        }
    }

    /**
     * Change the admin PIN (requires current PIN verification).
     */
    fun changePin(currentPin: String, newPin: String): Boolean {
        if (!verifyPin(currentPin)) return false
        return setPin(newPin)
    }

    /**
     * Check if too many failed attempts have triggered a lockout.
     */
    fun isLockedOut(): Boolean {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        if (lockoutUntil == 0L) return false

        return if (System.currentTimeMillis() < lockoutUntil) {
            true
        } else {
            // Lockout expired — reset
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .remove(KEY_LOCKOUT_UNTIL)
                .apply()
            false
        }
    }

    /**
     * Get remaining lockout time in milliseconds.
     */
    fun getRemainingLockoutMs(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        return maxOf(lockoutUntil - System.currentTimeMillis(), 0)
    }

    /**
     * Remove the admin PIN.
     */
    fun removePin(currentPin: String): Boolean {
        if (!verifyPin(currentPin)) return false
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_PIN_SET, false)
            .apply()
        return true
    }

    // ─── Private ───

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
