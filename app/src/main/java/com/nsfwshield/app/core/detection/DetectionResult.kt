package com.nsfwshield.app.core.detection

/**
 * Content categories for classification.
 */
enum class ContentCategory {
    EXPLICIT_SEXUAL,
    VIOLENCE_GORE,
    HATE_SPEECH,
    DRUG_REFERENCES,
    GAMBLING,
    SELF_HARM,
    SAFE
}

/**
 * Source of the detected content.
 */
enum class DetectionSource {
    IMAGE_CLASSIFIER,
    TEXT_CLASSIFIER,
    VIDEO_ANALYZER,
    AUDIO_HANDLER,
    DNS_FILTER,
    ACCESSIBILITY_SCAN,
    MEDIA_SCANNER
}

/**
 * Result of a content detection analysis.
 * Contains confidence score (0.0–1.0), category, and blocking decision.
 */
data class DetectionResult(
    val score: Float,
    val category: ContentCategory,
    val isBlocked: Boolean,
    val source: DetectionSource,
    val contextAdjusted: Boolean = false,
    val originalScore: Float = score,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Whether this result was rescored by the context rescorer.
     */
    val wasRescored: Boolean get() = contextAdjusted && originalScore != score

    /**
     * Human-readable label for the category.
     */
    val categoryLabel: String get() = when (category) {
        ContentCategory.EXPLICIT_SEXUAL -> "Explicit Sexual Content"
        ContentCategory.VIOLENCE_GORE -> "Violence/Gore"
        ContentCategory.HATE_SPEECH -> "Hate Speech"
        ContentCategory.DRUG_REFERENCES -> "Drug References"
        ContentCategory.GAMBLING -> "Gambling"
        ContentCategory.SELF_HARM -> "Self-Harm"
        ContentCategory.SAFE -> "Safe"
    }
}
