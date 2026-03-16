package com.nsfwshield.app.monitoring

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blur Overlay Manager.
 * Manages the blur/block overlay displayed over flagged content.
 *
 * Actions:
 * - BLUR — Gaussian blur overlay with semi-transparent shield
 * - REPLACE — Neutral placeholder with reason label
 * - BLOCK — Full-screen block with category and score info
 */
@Singleton
class BlurOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var blurOverlay: View? = null
    private var blockOverlay: View? = null
    private var isOverlayVisible = false

    companion object {
        private const val BLUR_ALPHA = 0.85f
        private const val BLUR_COLOR = 0xDD1A1C1E.toInt() // Dark semi-transparent
        private const val BLOCK_COLOR = 0xFF0F1419.toInt() // Full dark
    }

    /**
     * Apply a blur overlay over the entire screen.
     */
    suspend fun applyBlur() {
        if (isOverlayVisible) return

        withContext(Dispatchers.Main) {
            try {
                removeAllOverlaysInternal()

            val overlay = FrameLayout(context).apply {
                setBackgroundColor(BLUR_COLOR)
                alpha = BLUR_ALPHA

                // Add shield icon/text in center
                val label = TextView(context).apply {
                    text = "🛡️ Content Blocked by NSFW Shield"
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setPadding(32, 32, 32, 32)
                }
                addView(label, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                ))
            }

            val params = createOverlayParams()
            windowManager.addView(overlay, params)
            blurOverlay = overlay
            isOverlayVisible = true
            } catch (e: Exception) {
                // Failed to add overlay — permission may not be granted
                Log.e("BlurOverlayManager", "Failed to apply blur: ${e.message}", e)
            }
        }
    }

    /**
     * Apply a full block screen with category information.
     */
    suspend fun applyBlockScreen(category: String, score: Float = 0f) {
        if (isOverlayVisible) return

        withContext(Dispatchers.Main) {
            try {
                removeAllOverlaysInternal()

            val overlay = FrameLayout(context).apply {
                setBackgroundColor(BLOCK_COLOR)

                val container = FrameLayout(context).apply {
                    val icon = TextView(context).apply {
                        text = "🛡️"
                        textSize = 48f
                        gravity = Gravity.CENTER
                    }
                    addView(icon, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    ).apply { bottomMargin = 200 })

                    val title = TextView(context).apply {
                        text = "Content Blocked"
                        setTextColor(Color.WHITE)
                        textSize = 24f
                        gravity = Gravity.CENTER
                    }
                    addView(title, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    ).apply { bottomMargin = 80 })

                    val details = TextView(context).apply {
                        text = "Category: $category"
                        setTextColor(Color.parseColor("#B0BEC5"))
                        textSize = 14f
                        gravity = Gravity.CENTER
                    }
                    addView(details, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    ).apply { topMargin = 60 })

                    val instruction = TextView(context).apply {
                        text = "Navigate away from this page to continue"
                        setTextColor(Color.parseColor("#78909C"))
                        textSize = 12f
                        gravity = Gravity.CENTER
                    }
                    addView(instruction, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    ).apply { topMargin = 160 })
                }

                addView(container, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }

            val params = createOverlayParams()
            windowManager.addView(overlay, params)
            blockOverlay = overlay
            isOverlayVisible = true
            } catch (e: Exception) {
                // Failed to add overlay
                Log.e("BlurOverlayManager", "Failed to apply block screen: ${e.message}", e)
            }
        }
    }

    /**
     * Remove all overlays from the screen.
     */
    suspend fun removeAllOverlays() {
        withContext(Dispatchers.Main) {
            removeAllOverlaysInternal()
        }
    }
    
    private fun removeAllOverlaysInternal() {
        try {
            blurOverlay?.let {
                windowManager.removeView(it)
                blurOverlay = null
            }
            blockOverlay?.let {
                windowManager.removeView(it)
                blockOverlay = null
            }
            isOverlayVisible = false
        } catch (e: Exception) {
            Log.e("BlurOverlayManager", "Failed to remove overlays: ${e.message}", e)
            blurOverlay = null
            blockOverlay = null
            isOverlayVisible = false
        }
    }

    /**
     * Check if an overlay is currently visible.
     */
    fun isVisible(): Boolean = isOverlayVisible

    // ─── Private helpers ───

    private fun createOverlayParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }
}
