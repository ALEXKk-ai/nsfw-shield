package com.nsfwshield.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nsfwshield.app.data.SettingsRepository
import com.nsfwshield.app.network.BlocklistUpdateScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * NSFW Shield Application class.
 * Initializes Hilt dependency injection and configures WorkManager for the entire app.
 */
@HiltAndroidApp
class NSFWShieldApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var blocklistUpdateScheduler: BlocklistUpdateScheduler
    @Inject lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Schedule blocklist auto-update if enabled
        appScope.launch {
            try {
                val autoUpdate = settingsRepository.autoUpdateBlocklist.first()
                if (autoUpdate) {
                    val wifiOnly = settingsRepository.updateOnlyOnWifi.first()
                    blocklistUpdateScheduler.schedule(wifiOnly)
                }
            } catch (e: Exception) {
                Log.e("NSFWShieldApp", "Failed to schedule blocklist update", e)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
