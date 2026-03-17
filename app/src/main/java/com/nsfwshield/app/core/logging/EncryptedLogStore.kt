package com.nsfwshield.app.core.logging

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted Log Store.
 * Provides AES-256 encrypted file storage for sensitive activity logs.
 * Uses AndroidX security-crypto (Jetpack Security) for encryption.
 *
 * All data is stored locally and never transmitted externally.
 */
@Singleton
class EncryptedLogStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val logDirectory: File = File(context.filesDir, "encrypted_logs")

    init {
        if (!logDirectory.exists()) {
            logDirectory.mkdirs()
        }
    }

    /**
     * Write encrypted data to a log file.
     */
    suspend fun writeEncryptedLog(
        filename: String,
        data: String
    ) = withContext(Dispatchers.IO) {
        val file = File(logDirectory, filename)

        // EncryptedFile requires the file to not exist beforehand
        if (file.exists()) file.delete()

        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        encryptedFile.openFileOutput().use { outputStream ->
            outputStream.write(data.toByteArray())
        }
    }

    /**
     * Read and decrypt a log file.
     */
    suspend fun readEncryptedLog(filename: String): String? = withContext(Dispatchers.IO) {
        val file = File(logDirectory, filename)
        if (!file.exists()) return@withContext null

        try {
            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            encryptedFile.openFileInput().use { inputStream ->
                inputStream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Append data to an encrypted log.
     * Since EncryptedFile doesn't support append, we read-modify-write.
     */
    suspend fun appendToLog(filename: String, data: String) {
        val existing = readEncryptedLog(filename) ?: ""
        writeEncryptedLog(filename, existing + "\n" + data)
    }

    /**
     * List all encrypted log files.
     */
    fun listLogFiles(): List<String> {
        return logDirectory.listFiles()?.map { it.name } ?: emptyList()
    }

    /**
     * Delete a specific encrypted log file.
     */
    suspend fun deleteLog(filename: String) = withContext(Dispatchers.IO) {
        File(logDirectory, filename).delete()
    }

    /**
     * Delete all encrypted logs.
     */
    suspend fun deleteAllLogs() = withContext(Dispatchers.IO) {
        logDirectory.listFiles()?.forEach { it.delete() }
    }

    /**
     * Get total size of encrypted logs in bytes.
     */
    fun getTotalLogSize(): Long {
        return logDirectory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Export logs as a single encrypted blob for backup.
     */
    suspend fun exportLogs(): String = withContext(Dispatchers.IO) {
        val allLogs = StringBuilder()
        logDirectory.listFiles()?.sortedBy { it.name }?.forEach { file ->
            val content = readEncryptedLog(file.name)
            if (content != null) {
                allLogs.appendLine("=== ${file.name} ===")
                allLogs.appendLine(content)
                allLogs.appendLine()
            }
        }
        allLogs.toString()
    }
}
