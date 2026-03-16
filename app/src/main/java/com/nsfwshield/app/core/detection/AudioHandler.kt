package com.nsfwshield.app.core.detection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio content handler.
 * Transcribes audio using on-device Whisper (small model) and passes
 * the transcript through the TextClassifier.
 *
 * Processing is fully async and non-blocking to the UI thread.
 */
@Singleton
class AudioHandler @Inject constructor(
    private val textClassifier: TextClassifier
) {
    companion object {
        private const val MAX_AUDIO_DURATION_SECONDS = 300 // 5 minutes max
        private const val CHUNK_DURATION_SECONDS = 30      // Process in 30-second chunks
    }

    data class AudioAnalysisResult(
        val transcript: String,
        val textResults: List<DetectionResult>,
        val isFlagged: Boolean,
        val maxScore: Float,
        val dominantCategory: ContentCategory
    )

    /**
     * Analyze an audio file by transcribing and classifying the text content.
     */
    suspend fun analyzeAudio(audioPath: String): AudioAnalysisResult = withContext(Dispatchers.IO) {
        // Step 1: Transcribe audio using on-device model
        val transcript = transcribeAudio(audioPath)

        // Step 2: Classify transcript text
        val textResults = if (transcript.isNotBlank()) {
            textClassifier.classifyText(transcript)
        } else {
            emptyList()
        }

        // Step 3: Determine flagging
        val maxScore = textResults.maxOfOrNull { it.score } ?: 0f
        val isFlagged = textResults.any { it.score > 0.5f }
        val dominantCategory = textResults
            .filter { it.category != ContentCategory.SAFE }
            .maxByOrNull { it.score }
            ?.category ?: ContentCategory.SAFE

        AudioAnalysisResult(
            transcript = transcript,
            textResults = textResults,
            isFlagged = isFlagged,
            maxScore = maxScore,
            dominantCategory = dominantCategory
        )
    }

    /**
     * Analyze a pre-existing transcript (e.g., subtitles).
     */
    suspend fun analyzeTranscript(transcript: String): AudioAnalysisResult {
        val textResults = textClassifier.classifyText(transcript)
        val maxScore = textResults.maxOfOrNull { it.score } ?: 0f

        return AudioAnalysisResult(
            transcript = transcript,
            textResults = textResults,
            isFlagged = textResults.any { it.score > 0.5f },
            maxScore = maxScore,
            dominantCategory = textResults
                .filter { it.category != ContentCategory.SAFE }
                .maxByOrNull { it.score }
                ?.category ?: ContentCategory.SAFE
        )
    }

    // ─── Private helpers ───

    /**
     * Transcribe audio using on-device Whisper model.
     * This is a placeholder for the actual Whisper integration.
     * The actual implementation would use the TFLite Whisper small model.
     */
    private suspend fun transcribeAudio(audioPath: String): String {
        // TODO: Integrate on-device Whisper TFLite model
        // The implementation would:
        // 1. Load the Whisper small model (.tflite)
        // 2. Preprocess audio into mel spectrogram features
        // 3. Run inference in chunks of CHUNK_DURATION_SECONDS
        // 4. Decode tokens to text
        // For now, returns empty — text classifier handles direct text input
        return ""
    }
}
