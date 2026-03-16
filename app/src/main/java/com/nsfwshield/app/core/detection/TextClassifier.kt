package com.nsfwshield.app.core.detection

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text content classifier using a two-layer approach:
 * 1. Fast keyword database pass for obvious matches
 * 2. ML-based NLP classification for ambiguous content
 *
 * Categories: explicit sexual, violence/gore, hate speech, drug references, gambling, self-harm
 */
@Singleton
class TextClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // ─── Keyword databases per category ───
        private val EXPLICIT_KEYWORDS = setOf(
            "porn", "xxx", "nsfw", "nude", "naked", "hentai", "erotic",
            "explicit", "adult content", "onlyfans", "camgirl", "strip",
            "fetish", "bondage", "bdsm"
        )

        private val VIOLENCE_KEYWORDS = setOf(
            "gore", "murder", "killing", "torture", "mutilation",
            "beheading", "execution", "slaughter", "bloodbath", "dismember"
        )

        private val HATE_SPEECH_KEYWORDS = setOf(
            "white supremacy", "nazi", "racial slur", "ethnic cleansing",
            "genocide", "hate crime", "extremist", "terrorist propaganda"
        )

        private val DRUG_KEYWORDS = setOf(
            "cocaine", "heroin", "methamphetamine", "drug dealer",
            "drug trafficking", "fentanyl", "opioid abuse", "crack pipe",
            "illegal drugs", "drug lab"
        )

        private val GAMBLING_KEYWORDS = setOf(
            "online casino", "sports betting", "poker room", "slot machine",
            "gambling site", "bet365", "draft kings", "fanduel",
            "blackjack", "roulette online"
        )

        private val SELF_HARM_KEYWORDS = setOf(
            "suicide method", "self-harm", "cutting self", "end my life",
            "how to die", "suicide note", "kill myself"
        )

        private const val KEYWORD_HIT_THRESHOLD = 0.85f
        private const val AMBIGUOUS_THRESHOLD = 0.50f
    }

    /**
     * Classify text content and return detection results for each matching category.
     */
    suspend fun classifyText(text: String): List<DetectionResult> = withContext(Dispatchers.Default) {
        val normalizedText = text.lowercase().trim()
        val results = mutableListOf<DetectionResult>()

        // Layer 1: Fast keyword pass
        val keywordResults = performKeywordPass(normalizedText)
        results.addAll(keywordResults)

        // Layer 2: NLP classification for text not caught by keywords
        if (keywordResults.isEmpty() || keywordResults.all { it.score < AMBIGUOUS_THRESHOLD }) {
            val nlpResults = performNLPClassification(normalizedText)
            results.addAll(nlpResults)
        }

        // Return top result per category (highest score)
        results
            .groupBy { it.category }
            .map { (_, categoryResults) -> categoryResults.maxByOrNull { it.score }!! }
    }

    /**
     * Quick check if text contains explicit content (no NLP, keywords only).
     */
    fun quickCheck(text: String): Boolean {
        val normalized = text.lowercase()
        return EXPLICIT_KEYWORDS.any { normalized.contains(it) }
    }

    // ─── Private helpers ───

    private fun performKeywordPass(text: String): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()

        checkKeywords(text, EXPLICIT_KEYWORDS, ContentCategory.EXPLICIT_SEXUAL)?.let { results.add(it) }
        checkKeywords(text, VIOLENCE_KEYWORDS, ContentCategory.VIOLENCE_GORE)?.let { results.add(it) }
        checkKeywords(text, HATE_SPEECH_KEYWORDS, ContentCategory.HATE_SPEECH)?.let { results.add(it) }
        checkKeywords(text, DRUG_KEYWORDS, ContentCategory.DRUG_REFERENCES)?.let { results.add(it) }
        checkKeywords(text, GAMBLING_KEYWORDS, ContentCategory.GAMBLING)?.let { results.add(it) }
        checkKeywords(text, SELF_HARM_KEYWORDS, ContentCategory.SELF_HARM)?.let { results.add(it) }

        return results
    }

    private fun checkKeywords(
        text: String,
        keywords: Set<String>,
        category: ContentCategory
    ): DetectionResult? {
        val matchCount = keywords.count { text.contains(it) }
        if (matchCount == 0) return null

        // Score based on number of keyword matches
        val score = minOf(KEYWORD_HIT_THRESHOLD + (matchCount - 1) * 0.05f, 1.0f)

        return DetectionResult(
            score = score,
            category = category,
            isBlocked = false, // DecisionEngine makes blocking decisions
            source = DetectionSource.TEXT_CLASSIFIER,
            metadata = mapOf(
                "layer" to "keyword",
                "match_count" to matchCount.toString()
            )
        )
    }

    private suspend fun performNLPClassification(text: String): List<DetectionResult> {
        // ML Kit NLP classification would go here.
        // For now, returns empty — the keyword layer handles most cases.
        // When ML Kit is integrated, this runs the on-device NLP model
        // for ambiguous text that keywords didn't catch.
        return emptyList()
    }
}
