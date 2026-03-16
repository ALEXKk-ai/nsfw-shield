package com.nsfwshield.app.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nsfwshield.app.network.LocalVpnService

/**
 * Boot Receiver to auto-start NSFW Shield services after device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restart VPN service if it was previously active
            val prefs = context.getSharedPreferences("nsfw_shield_prefs", Context.MODE_PRIVATE)
            val vpnWasActive = prefs.getBoolean("vpn_active", false)

            if (vpnWasActive) {
                val vpnIntent = Intent(context, LocalVpnService::class.java).apply {
                    action = LocalVpnService.ACTION_START
                }
                context.startForegroundService(vpnIntent)
            }
        }
    }
}
