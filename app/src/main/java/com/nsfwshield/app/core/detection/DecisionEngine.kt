package com.nsfwshield.app.core.detection

import android.graphics.Bitmap
import com.nsfwshield.app.core.profiles.FilterProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central Decision Engine that combines scores from all classifiers,
 * applies profile-based thresholds, and returns the final block/allow decision.
 *
 * This is the single point where blocking decisions are made.
 */
@Singleton
class DecisionEngine @Inject constructor(
    private val imageClassifier: ImageClassifier,
    private val textClassifier: TextClassifier,
    private val contextRescorer: ContextRescorer,
    private val videoHandler: VideoHandler,
    private val audioHandler: AudioHandler
) {
    /**
     * Block action to take when content is flagged.
     */
    enum class BlockAction {
        BLUR,       // Gaussian blur overlay
        REPLACE,    // Neutral placeholder + reason label
        BLOCK,      // Prevent render, show block screen
        ALLOW       // Content passes
    }

    /**
     * Final decision result combining all analysis.
     */
    data class Decision(
        val action: BlockAction,
        val results: List<DetectionResult>,
        val primaryResult: DetectionResult?,
        val profileId: String,
        val requiresPinToOverride: Boolean
    )

    /**
     * Analyze an image against a filter profile.
     */
    suspend fun analyzeImage(
        image: Bitmap,
        profile: FilterProfile,
        context: ContextRescorer.ContentContext? = null
    ): Decision {
        var result = imageClassifier.detectNSFW(image)

        // Apply context rescoring if context is available
        if (context != null) {
            result = contextRescorer.rescore(result, context)
        }

        return makeDecision(listOf(result), profile)
    }

    /**
     * Analyze text content against a filter profile.
     */
    suspend fun analyzeText(
        text: String,
        profile: FilterProfile,
        context: ContextRescorer.ContentContext? = null
    ): Decision {
        var results = textClassifier.classifyText(text)

        // Apply context rescoring
        if (context != null) {
            results = results.map { contextRescorer.rescore(it, context) }
        }

        return makeDecision(results, profile)
    }

    /**
     * Analyze mixed content (image + text) against a filter profile.
     * Runs image and text analysis in parallel for performance.
     */
    suspend fun analyzeMixed(
        image: Bitmap?,
        text: String?,
        profile: FilterProfile,
        context: ContextRescorer.ContentContext? = null
    ): Decision = coroutineScope {
        val allResults = mutableListOf<DetectionResult>()

        val imageDeferred = image?.let {
            async { imageClassifier.detectNSFW(it) }
        }

        val textDeferred = text?.let {
            async { textClassifier.classifyText(it) }
        }

        imageDeferred?.await()?.let { result ->
            val rescored = context?.let { ctx -> contextRescorer.rescore(result, ctx) } ?: result
            allResults.add(rescored)
        }

        textDeferred?.await()?.let { results ->
            val rescored = if (context != null) {
                results.map { contextRescorer.rescore(it, context) }
            } else results
            allResults.addAll(rescored)
        }

        makeDecision(allResults, profile)
    }

    // ─── Private helpers ───

    /**
     * Core decision logic: applies profile thresholds and category toggles.
     */
    private fun makeDecision(
        results: List<DetectionResult>,
        profile: FilterProfile
    ): Decision {
        if (results.isEmpty()) {
            return Decision(
                action = BlockAction.ALLOW,
                results = emptyList(),
                primaryResult = null,
                profileId = profile.profileId,
                requiresPinToOverride = false
            )
        }

        val toggles = profile.getCategoryToggles()

        // Filter results to only enabled categories
        val relevantResults = results.filter { result ->
            when (result.category) {
                ContentCategory.EXPLICIT_SEXUAL -> toggles.explicitSexual
                ContentCategory.VIOLENCE_GORE -> toggles.violenceGore
                ContentCategory.HATE_SPEECH -> toggles.hateSpeech
                ContentCategory.DRUG_REFERENCES -> toggles.drugReferences
                ContentCategory.GAMBLING -> toggles.gambling
                ContentCategory.SELF_HARM -> toggles.selfHarm
                ContentCategory.SAFE -> false
            }
        }

        // Find the highest-scoring relevant result
        val primaryResult = relevantResults.maxByOrNull { it.score }

        // Determine if content should be blocked
        val shouldBlock = primaryResult != null &&
                primaryResult.score >= profile.confidenceThreshold

        val action = if (shouldBlock) {
            // Determine action based on sensitivity level
            when (profile.sensitivity) {
                com.nsfwshield.app.core.profiles.SensitivityLevel.STRICT -> BlockAction.BLOCK
                com.nsfwshield.app.core.profiles.SensitivityLevel.MODERATE -> BlockAction.BLUR
                com.nsfwshield.app.core.profiles.SensitivityLevel.CUSTOM -> BlockAction.BLUR
            }
        } else {
            BlockAction.ALLOW
        }

        // Mark results with final blocking decision
        val finalResults = results.map { result ->
            result.copy(
                isBlocked = relevantResults.contains(result) &&
                        result.score >= profile.confidenceThreshold
            )
        }

        return Decision(
            action = action,
            results = finalResults,
            primaryResult = primaryResult?.copy(isBlocked = shouldBlock),
            profileId = profile.profileId,
            requiresPinToOverride = profile.overridePinRequired && shouldBlock
        )
    }
}
