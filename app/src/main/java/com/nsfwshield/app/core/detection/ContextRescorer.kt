package com.nsfwshield.app.core.detection

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Context Rescorer reduces false positives by analyzing surrounding context.
 *
 * Wraps raw classifier output with context signals (page title, URL metadata,
 * surrounding text) and adjusts confidence scores accordingly.
 *
 * Target: ≥30% reduction in false positives vs. naive classification.
 *
 * Examples:
 * - "breast" + cooking context → rescored down
 * - "breast" + explicit URL → score preserved
 * - Medical anatomy diagrams → rescored down
 * - Art museum content → rescored down
 */
@Singleton
class ContextRescorer @Inject constructor() {

    companion object {
        // Context signals that indicate benign content
        private val MEDICAL_KEYWORDS = setOf(
            "medical", "anatomy", "health", "disease", "diagnosis", "treatment",
            "surgery", "clinical", "patient", "hospital", "doctor", "nurse",
            "medicine", "pathology", "radiology", "oncology", "dermatology",
            "mammogram", "biopsy", "examination", "symptom", "therapy"
        )

        private val EDUCATIONAL_KEYWORDS = setOf(
            "education", "biology", "science", "research", "study", "academic",
            "university", "lecture", "textbook", "course", "lesson", "anatomy",
            "museum", "gallery", "art history", "classical", "renaissance",
            "sculpture", "painting", "exhibition", "wikipedia", "encyclopedia"
        )

        private val CULINARY_KEYWORDS = setOf(
            "recipe", "cooking", "food", "kitchen", "chef", "baking",
            "ingredient", "roast", "breast", "thigh", "leg", "wing",
            "chicken", "turkey", "duck", "meat", "grill", "marinade",
            "cuisine", "dish", "restaurant", "menu"
        )

        private val SAFE_DOMAINS = setOf(
            "wikipedia.org", "webmd.com", "mayoclinic.org", "healthline.com",
            "nih.gov", "cdc.gov", "pubmed.ncbi.nlm.nih.gov", "medlineplus.gov",
            "allrecipes.com", "foodnetwork.com", "cooking.nytimes.com",
            "artsy.net", "moma.org", "metmuseum.org", "nga.gov",
            "khanacademy.org", "coursera.org", "edx.org"
        )

        // Rescoring factors
        private const val MEDICAL_DISCOUNT = 0.60f   // 60% reduction
        private const val EDUCATIONAL_DISCOUNT = 0.55f
        private const val CULINARY_DISCOUNT = 0.65f
        private const val SAFE_DOMAIN_DISCOUNT = 0.50f
        private const val MIN_SCORE_AFTER_RESCORE = 0.05f
    }

    /**
     * Context information for rescoring decisions.
     */
    data class ContentContext(
        val pageTitle: String? = null,
        val url: String? = null,
        val surroundingText: String? = null,
        val appPackage: String? = null
    )

    /**
     * Rescore a detection result using surrounding context.
     * Returns a new DetectionResult with adjusted score if context indicates benign content.
     */
    fun rescore(result: DetectionResult, context: ContentContext): DetectionResult {
        if (result.category == ContentCategory.SAFE) {
            return result // No need to rescore safe content
        }

        var discount = 1.0f // 1.0 = no change
        val reasons = mutableListOf<String>()

        // Check URL against safe domains
        context.url?.let { url ->
            val domain = extractDomain(url)
            if (SAFE_DOMAINS.any { domain.contains(it) }) {
                discount *= SAFE_DOMAIN_DISCOUNT
                reasons.add("safe_domain")
            }
        }

        // Check context text for medical keywords
        val allText = buildContextText(context)
        val medicalScore = calculateKeywordOverlap(allText, MEDICAL_KEYWORDS)
        if (medicalScore >= 2) {
            discount *= MEDICAL_DISCOUNT
            reasons.add("medical_context")
        }

        // Check context text for educational keywords
        val educationalScore = calculateKeywordOverlap(allText, EDUCATIONAL_KEYWORDS)
        if (educationalScore >= 2) {
            discount *= EDUCATIONAL_DISCOUNT
            reasons.add("educational_context")
        }

        // Check context text for culinary keywords
        val culinaryScore = calculateKeywordOverlap(allText, CULINARY_KEYWORDS)
        if (culinaryScore >= 2) {
            discount *= CULINARY_DISCOUNT
            reasons.add("culinary_context")
        }

        // Apply discount
        if (discount < 1.0f) {
            val newScore = maxOf(result.score * discount, MIN_SCORE_AFTER_RESCORE)
            return result.copy(
                score = newScore,
                contextAdjusted = true,
                originalScore = result.score,
                metadata = result.metadata + mapOf(
                    "rescore_reasons" to reasons.joinToString(","),
                    "discount_factor" to String.format("%.2f", discount)
                )
            )
        }

        return result
    }

    // ─── Private helpers ───

    private fun extractDomain(url: String): String {
        return try {
            url.removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
                .substringBefore("?")
                .lowercase()
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildContextText(context: ContentContext): String {
        return listOfNotNull(
            context.pageTitle,
            context.url,
            context.surroundingText
        ).joinToString(" ").lowercase()
    }

    private fun calculateKeywordOverlap(text: String, keywords: Set<String>): Int {
        return keywords.count { keyword -> text.contains(keyword) }
    }
}
