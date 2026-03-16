package com.nsfwshield.app.monitoring

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nsfwshield.app.core.detection.ContentCategory
import com.nsfwshield.app.core.detection.ContextRescorer
import com.nsfwshield.app.core.detection.DecisionEngine
import com.nsfwshield.app.core.detection.DetectionResult
import com.nsfwshield.app.core.detection.DetectionSource
import com.nsfwshield.app.core.logging.ActivityLogger
import com.nsfwshield.app.core.profiles.ProfileManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import android.util.Log
import javax.inject.Inject

/**
 * Accessibility Service for real-time screen monitoring.
 *
 * Monitors active screen content across browsers, social media, gallery apps,
 * and messaging apps. Detects explicit text and triggers blur overlays when
 * content exceeds the active profile's thresholds.
 *
 * PRIVACY: All processing happens entirely on-device. No screenshots or screen
 * content is ever uploaded, transmitted, or stored externally.
 */
@AndroidEntryPoint
class NSFWAccessibilityService : AccessibilityService() {

    @Inject lateinit var blurOverlayManager: BlurOverlayManager
    @Inject lateinit var decisionEngine: DecisionEngine
    @Inject lateinit var profileManager: ProfileManager
    @Inject lateinit var activityLogger: ActivityLogger

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Throttle: don't process events too frequently
    private var lastProcessedTime = 0L
    private val processingIntervalMs = 500L // Process at most every 500ms

    // Track current app to avoid redundant processing
    private var currentPackage: String? = null

    companion object {
        var isRunning = false
            private set

        // Apps to skip (system UI, keyboards, etc.)
        private val EXCLUDED_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.nsfwshield.app",  // Don't monitor ourselves
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true

        // Configure service
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 300
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < processingIntervalMs) return
        lastProcessedTime = now

        // Get the top-most active window package name (more reliable than event.packageName)
        val activePackage = rootInActiveWindow?.packageName?.toString() ?: event.packageName?.toString() ?: return
        
        // Add extra logging for debugging when we are testing
        Log.d("NSFWAccessibility", "Active package: $activePackage")

        if (activePackage in EXCLUDED_PACKAGES) {
            // If we transitioned to a safe app, clear any existing overlays
            if (blurOverlayManager.isVisible()) {
                GlobalScope.launch(Dispatchers.Main) {
                    blurOverlayManager.removeAllOverlays()
                }
            }
            return
        }

        currentPackage = activePackage

        serviceScope.launch {
            processEvent(event, activePackage)
        }
    }

    override fun onInterrupt() {
        isRunning = false
        GlobalScope.launch(Dispatchers.Main) {
            blurOverlayManager.removeAllOverlays()
        }
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        GlobalScope.launch(Dispatchers.Main) {
            blurOverlayManager.removeAllOverlays()
        }
        serviceScope.cancel()
    }

    /**
     * Process an accessibility event by extracting text and analyzing it.
     */
    private suspend fun processEvent(event: AccessibilityEvent, packageName: String) {
        try {
            // Extract visible text from the screen
            val screenText = extractScreenText(event)

            if (screenText.isBlank()) return
            
            Log.d("NSFWAccessibility", "Extracted text length: ${screenText.length}, sample: ${screenText.take(100)}")

            val profile = profileManager.getActiveProfile()
            val engine = decisionEngine

            // Build context for rescoring
            val context = ContextRescorer.ContentContext(
                surroundingText = screenText,
                appPackage = packageName
            )

            // Analyze text content
            val decision = engine.analyzeText(screenText, profile, context)

            when (decision.action) {
                DecisionEngine.BlockAction.BLUR -> {
                    blurOverlayManager.applyBlur()
                    logEvent(decision, packageName)
                }
                DecisionEngine.BlockAction.BLOCK -> {
                    blurOverlayManager.applyBlockScreen(
                        decision.primaryResult?.categoryLabel ?: "Explicit Content"
                    )
                    logEvent(decision, packageName)
                }
                DecisionEngine.BlockAction.REPLACE -> {
                    blurOverlayManager.applyBlur()
                    logEvent(decision, packageName)
                }
                DecisionEngine.BlockAction.ALLOW -> {
                    blurOverlayManager.removeAllOverlays()
                }
            }
        } catch (e: Exception) {
            // Silently handle errors to prevent service crash
        }
    }

    /**
     * Extract all visible text from the current screen.
     */
    private fun extractScreenText(event: AccessibilityEvent): String {
        val textBuilder = StringBuilder()

        // Get text from the event itself
        event.text?.forEach { text ->
            textBuilder.append(text).append(" ")
        }

        // Traverse the accessibility node tree for more text
        val rootNode = rootInActiveWindow ?: return textBuilder.toString()
        traverseNode(rootNode, textBuilder, depth = 0, maxDepth = 10)

        return textBuilder.toString().trim()
    }

    /**
     * Recursively traverse accessibility nodes to extract text.
     */
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        textBuilder: StringBuilder,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return

        node.text?.let { text ->
            textBuilder.append(text).append(" ")
        }

        node.contentDescription?.let { desc ->
            textBuilder.append(desc).append(" ")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, textBuilder, depth + 1, maxDepth)
            child.recycle()
        }
    }

    private fun logEvent(decision: DecisionEngine.Decision, packageName: String) {
        serviceScope.launch {
            activityLogger.logBlockedEvent(
                category = decision.primaryResult?.category?.name ?: "UNKNOWN",
                source = "ACCESSIBILITY_SCAN",
                score = decision.primaryResult?.score ?: 0f,
                profileId = decision.profileId,
                appPackage = packageName,
                action = decision.action.name
            )
        }
    }
}
