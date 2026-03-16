package com.nsfwshield.app.core.detection

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Video content analyzer.
 * Extracts keyframes at configurable intervals and passes each frame
 * through the ImageClassifier. Flags video if >N frames exceed threshold
 * within a sliding window.
 */
@Singleton
class VideoHandler @Inject constructor(
    private val imageClassifier: ImageClassifier
) {
    companion object {
        private const val DEFAULT_FRAME_INTERVAL_MS = 3000L  // 1 frame per 3 seconds
        private const val DEFAULT_FLAG_THRESHOLD = 3          // Flag if 3+ frames exceed threshold
        private const val DEFAULT_WINDOW_SIZE = 10            // Sliding window of 10 frames
    }

    data class VideoAnalysisConfig(
        val frameIntervalMs: Long = DEFAULT_FRAME_INTERVAL_MS,
        val flagThreshold: Int = DEFAULT_FLAG_THRESHOLD,
        val windowSize: Int = DEFAULT_WINDOW_SIZE
    )

    data class VideoAnalysisResult(
        val totalFramesAnalyzed: Int,
        val flaggedFrames: Int,
        val maxScore: Float,
        val dominantCategory: ContentCategory,
        val isFlagged: Boolean,
        val frameResults: List<DetectionResult>
    )

    /**
     * Analyze a video file by extracting and classifying keyframes.
     */
    suspend fun analyzeVideo(
        videoPath: String,
        config: VideoAnalysisConfig = VideoAnalysisConfig()
    ): VideoAnalysisResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val frameResults = mutableListOf<DetectionResult>()

        try {
            retriever.setDataSource(videoPath)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            var timeMs = 0L
            while (timeMs < durationMs) {
                val frame = retriever.getFrameAtTime(
                    timeMs * 1000, // Convert to microseconds
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )

                frame?.let { bitmap ->
                    val result = imageClassifier.detectNSFW(bitmap)
                    frameResults.add(result)
                    bitmap.recycle()
                }

                timeMs += config.frameIntervalMs
            }
        } catch (e: Exception) {
            // Handle corrupted or inaccessible video files
        } finally {
            retriever.release()
        }

        analyzeFrameResults(frameResults, config)
    }

    /**
     * Analyze a single video frame (for real-time streaming analysis).
     */
    suspend fun analyzeFrame(frame: Bitmap): DetectionResult {
        return imageClassifier.detectNSFW(frame)
    }

    // ─── Private helpers ───

    private fun analyzeFrameResults(
        results: List<DetectionResult>,
        config: VideoAnalysisConfig
    ): VideoAnalysisResult {
        if (results.isEmpty()) {
            return VideoAnalysisResult(
                totalFramesAnalyzed = 0,
                flaggedFrames = 0,
                maxScore = 0f,
                dominantCategory = ContentCategory.SAFE,
                isFlagged = false,
                frameResults = emptyList()
            )
        }

        val maxScore = results.maxOf { it.score }
        val flaggedFrames = results.count { it.score > 0.5f }

        // Sliding window check
        var isFlagged = false
        for (i in 0..results.size - config.windowSize) {
            val window = results.subList(i, i + config.windowSize)
            val flaggedInWindow = window.count { it.score > 0.5f }
            if (flaggedInWindow >= config.flagThreshold) {
                isFlagged = true
                break
            }
        }

        // Also flag if total flagged frames exceed threshold
        if (flaggedFrames >= config.flagThreshold) {
            isFlagged = true
        }

        // Find dominant category
        val dominantCategory = results
            .filter { it.category != ContentCategory.SAFE }
            .groupBy { it.category }
            .maxByOrNull { it.value.size }
            ?.key ?: ContentCategory.SAFE

        return VideoAnalysisResult(
            totalFramesAnalyzed = results.size,
            flaggedFrames = flaggedFrames,
            maxScore = maxScore,
            dominantCategory = dominantCategory,
            isFlagged = isFlagged,
            frameResults = results
        )
    }
}
