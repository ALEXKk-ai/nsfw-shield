package com.nsfwshield.app.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uninstall Protection using Device Admin API.
 * Prevents unauthorized uninstallation of NSFW Shield.
 */
@Singleton
class UninstallProtection @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        ComponentName(context, DeviceAdminReceiver::class.java)

    /**
     * Check if device admin is active.
     */
    fun isAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    /**
     * Enable uninstall protection.
     */
    fun enableProtection() {
        if (isAdminActive()) {
            try {
                devicePolicyManager.setUninstallBlocked(
                    adminComponent,
                    context.packageName,
                    true
                )
            } catch (e: Exception) {
                // Ignore or log
            }
        }
    }

    /**
     * Disable uninstall protection.
     */
    fun disableProtection() {
        if (isAdminActive()) {
            try {
                devicePolicyManager.setUninstallBlocked(
                    adminComponent,
                    context.packageName,
                    false
                )
            } catch (e: Exception) {
                // Ignore or log
            }
        }
    }

    /**
     * Remove device admin privileges.
     */
    fun removeAdmin() {
        if (isAdminActive()) {
            devicePolicyManager.removeActiveAdmin(adminComponent)
        }
    }
}
