package com.nsfwshield.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

/**
 * VPN / Local Proxy Disclosure Screen.
 *
 * PLAY STORE MANDATORY: This full-screen pre-permission UI MUST appear
 * before the app activates the VPN Service. Same requirements as the
 * Accessibility disclosure — full screen, NOT a dialog.
 */
@Composable
fun VpnDisclosureScreen(
    onAccepted: () -> Unit,
    onNavigateBack: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Launcher for the system VPN permission dialog
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // If the user accepted (or denied), we check again
        if (VpnService.prepare(context) == null) {
            onAccepted()
        }
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

            // VPN icon
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF00BFA5).copy(alpha = 0.15f),
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VpnLock,
                    contentDescription = null,
                    tint = Color(0xFF00BFA5),
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "How NSFW Shield uses a VPN connection",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "NSFW Shield creates a local VPN connection on your device. " +
                        "This is used exclusively for DNS-level filtering — to block " +
                        "connections to known explicit content domains before they load.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ─── What this means ───
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E2D3D),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "What this means:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val items = listOf(
                        Pair(Icons.Default.Check, "Your traffic is filtered locally on your device only"),
                        Pair(Icons.Default.Check, "No data is routed through any external server"),
                        Pair(Icons.Default.Check, "No browsing data, URLs, or content is ever sent outside your device"),
                        Pair(Icons.Default.Close, "This is not a privacy VPN — it does not change your IP address or encrypt traffic for anonymity")
                    )

                    items.forEachIndexed { index, (icon, text) ->
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (index < 3) Color(0xFF66BB6A) else Color(0xFFFFA726),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB0BEC5),
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Purpose statement
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF00BFA5).copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF00BFA5),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "The local VPN is used solely to intercept DNS requests and block flagged domains.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF80CBC4),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Accept button
            Button(
                onClick = {
                    val intent = VpnService.prepare(context)
                    if (intent != null) {
                        vpnPermissionLauncher.launch(intent)
                    } else {
                        onAccepted()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00BFA5)
                )
            ) {
                Text(
                    text = "I Understand — Enable Filtering",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
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
                    text = "Go Back",
                    color = Color(0xFF455A64),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
