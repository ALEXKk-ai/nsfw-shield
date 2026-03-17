package com.nsfwshield.app.core.detection

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the ContextRescorer.
 * Verifies score adjustments for medical, educational, culinary, and safe domain contexts.
 */
class ContextRescorerTest {

    private lateinit var rescorer: ContextRescorer

    @Before
    fun setUp() {
        rescorer = ContextRescorer()
    }

    @Test
    fun `medical context reduces explicit score`() {
        val context = ContextRescorer.ContentContext(
            surroundingText = "breast cancer mammography screening procedure"
        )
        val result = DetectionResult(
            score = 0.85f,
            category = ContentCategory.EXPLICIT_SEXUAL,
            source = DetectionSource.IMAGE_AI,
            isBlocked = true
        )

        val rescored = rescorer.rescore(result, context)

        assertTrue("Medical context should reduce score", rescored.score < result.score)
        assertTrue("Should be marked as context rescored", rescored.contextRescored)
    }

    @Test
    fun `educational context reduces explicit score`() {
        val context = ContextRescorer.ContentContext(
            surroundingText = "anatomy biology human reproductive system textbook"
        )
        val result = DetectionResult(
            score = 0.80f,
            category = ContentCategory.EXPLICIT_SEXUAL,
            source = DetectionSource.IMAGE_AI,
            isBlocked = true
        )

        val rescored = rescorer.rescore(result, context)

        assertTrue("Educational context should reduce score", rescored.score < result.score)
    }

    @Test
    fun `culinary context reduces gore score`() {
        val context = ContextRescorer.ContentContext(
            surroundingText = "knife cutting raw meat recipe cooking preparation"
        )
        val result = DetectionResult(
            score = 0.75f,
            category = ContentCategory.VIOLENCE_GORE,
            source = DetectionSource.IMAGE_AI,
            isBlocked = true
        )

        val rescored = rescorer.rescore(result, context)

        assertTrue("Culinary context should reduce gore score", rescored.score < result.score)
    }

    @Test
    fun `safe domain reduces score`() {
        val context = ContextRescorer.ContentContext(
            domain = "mayoclinic.org"
        )
        val result = DetectionResult(
            score = 0.88f,
            category = ContentCategory.EXPLICIT_SEXUAL,
            source = DetectionSource.IMAGE_AI,
            isBlocked = true
        )

        val rescored = rescorer.rescore(result, context)

        assertTrue("Safe domain should reduce score", rescored.score < result.score)
    }

    @Test
    fun `no context does not change score`() {
        val context = ContextRescorer.ContentContext()
        val result = DetectionResult(
            score = 0.90f,
            category = ContentCategory.EXPLICIT_SEXUAL,
            source = DetectionSource.IMAGE_AI,
            isBlocked = true
        )

        val rescored = rescorer.rescore(result, context)

        assertEquals("No context should not change score", result.score, rescored.score, 0.01f)
    }

    @Test
    fun `explicit content with no safe context stays blocked`() {
        val context = ContextRescorer.ContentContext(
            surroundingText = "some random text without safe keywords"
        )
        val result = DetectionResult(
            score = 0.95f,
            category = ContentCategory.EXPLICIT_SEXUAL,
            source = DetectionSource.IMAGE_AI,
            isBlocked = true
        )

        val rescored = rescorer.rescore(result, context)

        // Score should be unchanged or minimally affected
        assertTrue("High-confidence explicit should remain high", rescored.score >= 0.90f)
    }
}
