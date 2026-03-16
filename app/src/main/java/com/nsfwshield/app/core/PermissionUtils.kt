package com.nsfwshield.app.core

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.nsfwshield.app.monitoring.NSFWAccessibilityService

object PermissionUtils {
    /**
     * Checks if the Accessibility Service is enabled in System Settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedServiceName = context.packageName + "/" + NSFWAccessibilityService::class.java.canonicalName
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
