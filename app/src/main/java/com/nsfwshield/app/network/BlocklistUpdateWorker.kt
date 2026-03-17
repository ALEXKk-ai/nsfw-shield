package com.nsfwshield.app.network

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nsfwshield.app.data.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Background worker that periodically fetches an updated domain blocklist
 * from a remote JSON endpoint, verifies its HMAC signature, and merges
 * the new domains into the local Room database.
 *
 * Expected JSON format:
 * {
 *   "version": 12,
 *   "signature": "hex-encoded-hmac-sha256",
 *   "domains": {
 *     "adult": ["site1.com", "site2.com"],
 *     "gambling": ["casino1.com"],
 *     "drugs": [],
 *     "gore": [],
 *     "self_harm": [],
 *     "hate_speech": []
 *   }
 * }
 */
@HiltWorker
class BlocklistUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val domainBlocklist: DomainBlocklist,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BlocklistUpdate"
        const val WORK_NAME = "blocklist_auto_update"

        // Production URL for automated blocklist updates
        private const val BLOCKLIST_URL = "https://raw.githubusercontent.com/ALEXKk-ai/nsfw-shield/main/scripts/blocklist.json"

        // HMAC secret key for signature verification
        // In production, this should be stored more securely (e.g., NDK)
        private const val HMAC_SECRET = "nsfw-shield-blocklist-signing-key-2026"
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting blocklist update check...")

                // Fetch the JSON from the remote URL
                val jsonString = fetchBlocklistJson() ?: run {
                    Log.w(TAG, "Failed to fetch blocklist JSON")
                    return@withContext Result.retry()
                }

                val json = JSONObject(jsonString)
                val remoteVersion = json.getInt("version")
                val signature = json.getString("signature")
                val domainsObj = json.getJSONObject("domains")

                // Check version — skip if we already have this version
                val currentVersion = settingsRepository.blocklistVersion.first()
                if (remoteVersion <= currentVersion) {
                    Log.i(TAG, "Blocklist already up to date (v$currentVersion)")
                    return@withContext Result.success()
                }

                // Verify HMAC signature
                val domainsString = domainsObj.toString()
                if (!verifySignature(domainsString, signature)) {
                    Log.e(TAG, "Blocklist signature verification FAILED — update rejected")
                    return@withContext Result.failure()
                }

                // Parse categorized domains
                val categorizedDomains = mutableMapOf<String, List<String>>()
                val keys = domainsObj.keys()
                while (keys.hasNext()) {
                    val category = keys.next()
                    val domainArray = domainsObj.getJSONArray(category)
                    val domainList = mutableListOf<String>()
                    for (i in 0 until domainArray.length()) {
                        domainList.add(domainArray.getString(i))
                    }
                    categorizedDomains[category] = domainList
                }

                // Merge into local database
                val totalDomains = categorizedDomains.values.sumOf { it.size }
                Log.i(TAG, "Merging $totalDomains domains across ${categorizedDomains.size} categories (v$remoteVersion)")
                domainBlocklist.updateFromRemoteCategorized(categorizedDomains)

                // Update metadata
                settingsRepository.setBlocklistVersion(remoteVersion)
                settingsRepository.setLastBlocklistUpdate(System.currentTimeMillis())

                Log.i(TAG, "Blocklist updated successfully to v$remoteVersion")
                Result.success()

            } catch (e: Exception) {
                Log.e(TAG, "Blocklist update failed", e)
                Result.retry()
            }
        }
    }

    /**
     * Fetch the blocklist JSON string from the remote URL.
     */
    private fun fetchBlocklistJson(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(BLOCKLIST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.w(TAG, "HTTP ${connection.responseCode} from blocklist URL")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching blocklist", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Verify HMAC-SHA256 signature of the domains JSON.
     */
    private fun verifySignature(data: String, expectedSignature: String): Boolean {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val keySpec = SecretKeySpec(HMAC_SECRET.toByteArray(), "HmacSHA256")
            mac.init(keySpec)
            val computedHash = mac.doFinal(data.toByteArray())
            val computedHex = computedHash.joinToString("") { "%02x".format(it) }
            computedHex == expectedSignature
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification error", e)
            false
        }
    }
}
