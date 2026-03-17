package com.nsfwshield.app.monitoring

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.work.*
import com.nsfwshield.app.core.detection.DecisionEngine
import com.nsfwshield.app.core.detection.DetectionSource
import com.nsfwshield.app.core.logging.ActivityLogger
import com.nsfwshield.app.core.profiles.ProfileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline Media Scanner.
 * Scans device storage folders (DCIM, Downloads, messaging media) for
 * explicit images and moves flagged content to a protected folder.
 *
 * Scheduling: on-demand, nightly, or on charge (via WorkManager).
 */
@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val decisionEngine: DecisionEngine,
    private val profileManager: ProfileManager
) {
    companion object {
        private val SCAN_DIRECTORIES = listOf(
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_PICTURES
        )

        private val MESSAGING_FOLDERS = listOf(
            "WhatsApp/Media/WhatsApp Images",
            "Telegram/Telegram Images",
            "Signal"
        )

        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        private const val PROTECTED_FOLDER = ".nsfw_shield_vault"
        private const val WORK_TAG = "nsfw_shield_media_scan"
    }

    data class ScanResult(
        val totalScanned: Int,
        val flaggedCount: Int,
        val movedCount: Int,
        val errors: Int,
        val durationMs: Long
    )

    /**
     * Run an on-demand scan of all target folders.
     */
    suspend fun scanAll(): ScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var totalScanned = 0
        var flaggedCount = 0
        var movedCount = 0
        var errors = 0

        val profile = profileManager.getActiveProfile()

        // Scan standard directories
        for (dirName in SCAN_DIRECTORIES) {
            val dir = Environment.getExternalStoragePublicDirectory(dirName)
            if (dir.exists()) {
                val result = scanFolder(dir, profile)
                totalScanned += result.totalScanned
                flaggedCount += result.flaggedCount
                movedCount += result.movedCount
                errors += result.errors
            }
        }

        // Scan messaging folders
        val externalStorage = Environment.getExternalStorageDirectory()
        for (folderPath in MESSAGING_FOLDERS) {
            val dir = File(externalStorage, folderPath)
            if (dir.exists()) {
                val result = scanFolder(dir, profile)
                totalScanned += result.totalScanned
                flaggedCount += result.flaggedCount
                movedCount += result.movedCount
                errors += result.errors
            }
        }

        ScanResult(
            totalScanned = totalScanned,
            flaggedCount = flaggedCount,
            movedCount = movedCount,
            errors = errors,
            durationMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Schedule periodic scanning using WorkManager.
     */
    fun scheduleNightlyScan() {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val scanRequest = PeriodicWorkRequestBuilder<MediaScanWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            scanRequest
        )
    }

    /**
     * Cancel scheduled scans.
     */
    fun cancelScheduledScans() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG)
    }

    /**
     * Scan a specific folder for explicit images.
     */
    private suspend fun scanFolder(
        folder: File,
        profile: com.nsfwshield.app.core.profiles.FilterProfile
    ): ScanResult {
        var scanned = 0
        var flagged = 0
        var moved = 0
        var errors = 0

        folder.walkTopDown().forEach { file ->
            if (file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS) {
                scanned++
                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEach
                    val decision = decisionEngine.analyzeImage(bitmap, profile)
                    bitmap.recycle()

                    if (decision.action != DecisionEngine.BlockAction.ALLOW) {
                        flagged++
                        if (moveToProtectedFolder(file)) {
                            moved++
                        }
                    }
                } catch (e: Exception) {
                    errors++
                }
            }
        }

        return ScanResult(scanned, flagged, moved, errors, 0)
    }

    /**
     * Move a flagged file to the protected vault folder.
     */
    private fun moveToProtectedFolder(file: File): Boolean {
        return try {
            val vaultDir = File(context.filesDir, PROTECTED_FOLDER)
            if (!vaultDir.exists()) vaultDir.mkdirs()

            val dest = File(vaultDir, file.name)
            file.renameTo(dest)
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * WorkManager worker for background media scanning.
 */
class MediaScanWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // In production, inject MediaScanner via Hilt WorkerFactory
        return Result.success()
    }
}
