package com.nsfwshield.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nsfw_shield_settings")

/**
 * Repository for persisting user settings via DataStore Preferences.
 * All toggles survive app restarts and are exposed as Flows for reactive UI.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val VPN_ENABLED = booleanPreferencesKey("vpn_enabled")
        val ACCESSIBILITY_ENABLED = booleanPreferencesKey("accessibility_enabled")
        val SAFE_SEARCH_ENABLED = booleanPreferencesKey("safe_search_enabled")
        val MEDIA_SCAN_ENABLED = booleanPreferencesKey("media_scan_enabled")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val PROTECTION_ACTIVE = booleanPreferencesKey("protection_active")
        val ADMIN_PIN = stringPreferencesKey("admin_pin")
        val UNINSTALL_PROTECTION = booleanPreferencesKey("uninstall_protection")
        val PARTNER_EMAIL = stringPreferencesKey("partner_email")
        val PARTNER_NAME = stringPreferencesKey("partner_name")
        val REPORT_FREQUENCY = stringPreferencesKey("report_frequency")
        val DISABLE_REQUEST_TIME = longPreferencesKey("disable_request_time")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val AUTO_UPDATE_BLOCKLIST = booleanPreferencesKey("auto_update_blocklist")
        val UPDATE_ONLY_ON_WIFI = booleanPreferencesKey("update_only_on_wifi")
        val BLOCKLIST_VERSION = intPreferencesKey("blocklist_version")
        val LAST_BLOCKLIST_UPDATE = longPreferencesKey("last_blocklist_update")
    }

    val vpnEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.VPN_ENABLED] ?: true }
    val accessibilityEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.ACCESSIBILITY_ENABLED] ?: true }
    val safeSearchEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SAFE_SEARCH_ENABLED] ?: true }
    val mediaScanEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.MEDIA_SCAN_ENABLED] ?: false }
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.DARK_MODE] ?: true }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    val protectionActive: Flow<Boolean> = context.dataStore.data.map { it[Keys.PROTECTION_ACTIVE] ?: true }
    val adminPin: Flow<String?> = context.dataStore.data.map { it[Keys.ADMIN_PIN] }
    val uninstallProtectionEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.UNINSTALL_PROTECTION] ?: false }
    val partnerEmail: Flow<String?> = context.dataStore.data.map { it[Keys.PARTNER_EMAIL] }
    val partnerName: Flow<String?> = context.dataStore.data.map { it[Keys.PARTNER_NAME] }
    val reportFrequency: Flow<String> = context.dataStore.data.map { it[Keys.REPORT_FREQUENCY] ?: "Weekly" }
    val disableRequestTime: Flow<Long?> = context.dataStore.data.map { it[Keys.DISABLE_REQUEST_TIME] }
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }
    val autoUpdateBlocklist: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_UPDATE_BLOCKLIST] ?: true }
    val updateOnlyOnWifi: Flow<Boolean> = context.dataStore.data.map { it[Keys.UPDATE_ONLY_ON_WIFI] ?: true }
    val blocklistVersion: Flow<Int> = context.dataStore.data.map { it[Keys.BLOCKLIST_VERSION] ?: 0 }
    val lastBlocklistUpdate: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_BLOCKLIST_UPDATE] }

    suspend fun setVpnEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VPN_ENABLED] = enabled }
    }

    suspend fun setAccessibilityEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ACCESSIBILITY_ENABLED] = enabled }
    }

    suspend fun setSafeSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SAFE_SEARCH_ENABLED] = enabled }
    }

    suspend fun setMediaScanEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MEDIA_SCAN_ENABLED] = enabled }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setProtectionActive(active: Boolean) {
        context.dataStore.edit { it[Keys.PROTECTION_ACTIVE] = active }
    }

    suspend fun setAdminPin(pin: String?) {
        context.dataStore.edit { prefs ->
            if (pin == null) prefs.remove(Keys.ADMIN_PIN) else prefs[Keys.ADMIN_PIN] = pin
        }
    }

    suspend fun setUninstallProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UNINSTALL_PROTECTION] = enabled }
    }

    suspend fun setPartnerInfo(email: String?, name: String?) {
        context.dataStore.edit { prefs ->
            if (email == null) prefs.remove(Keys.PARTNER_EMAIL) else prefs[Keys.PARTNER_EMAIL] = email
            if (name == null) prefs.remove(Keys.PARTNER_NAME) else prefs[Keys.PARTNER_NAME] = name
        }
    }

    suspend fun setReportFrequency(frequency: String) {
        context.dataStore.edit { it[Keys.REPORT_FREQUENCY] = frequency }
    }

    suspend fun setDisableRequestTime(timestamp: Long?) {
        context.dataStore.edit { prefs ->
            if (timestamp == null) prefs.remove(Keys.DISABLE_REQUEST_TIME) else prefs[Keys.DISABLE_REQUEST_TIME] = timestamp
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setAutoUpdateBlocklist(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_UPDATE_BLOCKLIST] = enabled }
    }

    suspend fun setUpdateOnlyOnWifi(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UPDATE_ONLY_ON_WIFI] = enabled }
    }

    suspend fun setBlocklistVersion(version: Int) {
        context.dataStore.edit { it[Keys.BLOCKLIST_VERSION] = version }
    }

    suspend fun setLastBlocklistUpdate(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_BLOCKLIST_UPDATE] = timestamp }
    }
}
