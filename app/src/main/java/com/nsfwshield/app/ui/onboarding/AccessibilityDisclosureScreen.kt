package com.nsfwshield.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.nsfwshield.app.core.PermissionUtils

/**
 * Accessibility Service Disclosure Screen.
 *
 * PLAY STORE MANDATORY: This full-screen pre-permission UI MUST appear
 * before the app requests Accessibility Service permission. It must be
 * a standalone, full-screen UI — NOT a dialog or tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityDisclosureScreen(
    onAccepted: () -> Unit,
    onNavigateBack: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

    // track the actual permission states
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityGranted by remember { mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(context)) }

    // Re-check permissions when user returns to the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isOverlayGranted = Settings.canDrawOverlays(context)
                isAccessibilityGranted = PermissionUtils.isAccessibilityServiceEnabled(context)
                
                // If everything is granted, auto-proceed
                if (isOverlayGranted && isAccessibilityGranted) {
                    onAccepted()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Launcher for permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Checking is handled in ON_RESUME, but we can do a quick check here too
        isOverlayGranted = Settings.canDrawOverlays(context)
        isAccessibilityGranted = PermissionUtils.isAccessibilityServiceEnabled(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1419),
                        Color(0xFF1A2332),
                        Color(0xFF0F1419)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Icon logic: change icon or color based on progress?
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1A73E8).copy(alpha = 0.15f),
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = Color(0xFF1A73E8),
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "How NSFW Shield uses Accessibility",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "NSFW Shield uses Android's Accessibility Service to detect and " +
                        "blur explicit images and text directly on your screen — across " +
                        "browsers, social media, and other apps.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ─── What we DO ───
            DisclosureSection(
                title = "What we DO:",
                items = listOf(
                    "Detect visual content on your screen in real time",
                    "Apply blur or block overlays over explicit content",
                    "Log detection events locally on your device"
                ),
                icon = Icons.Default.Check,
                iconColor = Color(0xFF66BB6A)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ─── What we NEVER do ───
            DisclosureSection(
                title = "What we NEVER do:",
                items = listOf(
                    "Upload, transmit, or store any screenshots or screen content externally",
                    "Read your messages, passwords, or personal data",
                    "Share any screen data with third parties"
                ),
                icon = Icons.Default.Close,
                iconColor = Color(0xFFEF5350)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Privacy badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1B5E20).copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF66BB6A),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "All processing happens entirely on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF81C784),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Permission status indicators
            if (!isOverlayGranted || !isAccessibilityGranted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    PermissionStatusItem(
                        label = "1. Overlay Permission",
                        isGranted = isOverlayGranted
                    )
                    PermissionStatusItem(
                        label = "2. Accessibility Service",
                        isGranted = isAccessibilityGranted
                    )
                }
            }

            // Dual-Action Button
            Button(
                onClick = {
                    when {
                        !isOverlayGranted -> {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            permissionLauncher.launch(intent)
                        }
                        !isAccessibilityGranted -> {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            permissionLauncher.launch(intent)
                        }
                        else -> {
                            onAccepted()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A73E8)
                )
            ) {
                Text(
                    text = when {
                        !isOverlayGranted -> "Grant Overlay Permission"
                        !isAccessibilityGranted -> "Enable Accessibility Service"
                        else -> "Continue"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Skip for now",
                    color = Color(0xFF78909C),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Go Back to Overview",
                    color = Color(0xFF455A64),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PermissionStatusItem(
    label: String,
    isGranted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF66BB6A) else Color(0xFF78909C),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isGranted) Color(0xFF81C784) else Color(0xFFB0BEC5),
            fontWeight = if (isGranted) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun DisclosureSection(
    title: String,
    items: List<String>,
    icon: ImageVector,
    iconColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E2D3D),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { item ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0BEC5),
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}
