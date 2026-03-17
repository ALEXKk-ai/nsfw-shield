package com.nsfwshield.app.ui.pinlock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * PIN Lock screen.
 * Entry for setting up and verifying the admin PIN.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinLockScreen(
    onNavigateBack: () -> Unit,
    forceSetup: Boolean = false,
    viewModel: PinLockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val effectiveIsPinSet = uiState.isPinSet && !forceSetup

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmMode by remember { mutableStateOf(false) }
    var showSuccessOverlay by remember { mutableStateOf(false) }
    var justFinishedSetup by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf(uiState.error) }
    val maxPinLength = 6

    // Handle success back navigation with delay
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            if (justFinishedSetup || forceSetup) {
                // We just set or changed the PIN
                showSuccessOverlay = true
                kotlinx.coroutines.delay(1500)
            }
            viewModel.resetSuccessState()
            onNavigateBack()
        }
    }

    // Sync error state
    LaunchedEffect(uiState.error) {
        errorMessage = uiState.error
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin PIN", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Lock icon
            Surface(
                shape = CircleShape,
                color = Color(0xFF1A73E8).copy(alpha = 0.12f),
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = if (effectiveIsPinSet) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF1A73E8),
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when {
                    effectiveIsPinSet -> "Enter Current PIN"
                    isConfirmMode -> "Confirm Your PIN"
                    else -> if (forceSetup) "Create New PIN" else "Create Admin PIN"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    effectiveIsPinSet -> "Enter your PIN to access settings"
                    isConfirmMode -> "Re-enter your PIN to confirm"
                    else -> "Set a 4–6 digit PIN to protect settings"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // PIN dots
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                val currentPin = if (isConfirmMode) confirmPin else pin
                repeat(maxPinLength) { index ->
                    Surface(
                        shape = CircleShape,
                        color = if (index < currentPin.length) Color(0xFF1A73E8)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(16.dp)
                    ) {}
                }
            }

            // Error message
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error,
                    color = Color(0xFFEF5350),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Number pad
            val numbers = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            )

            numbers.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { digit ->
                        if (digit.isEmpty()) {
                            Spacer(modifier = Modifier.size(72.dp))
                        } else {
                            Surface(
                                modifier = Modifier
                                    .size(72.dp)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        val currentPin = if (isConfirmMode) confirmPin else pin
                                        errorMessage = null

                                        if (digit == "⌫") {
                                            if (isConfirmMode && confirmPin.isNotEmpty()) {
                                                confirmPin = confirmPin.dropLast(1)
                                            } else if (!isConfirmMode && pin.isNotEmpty()) {
                                                pin = pin.dropLast(1)
                                            }
                                        } else if (currentPin.length < maxPinLength) {
                                            if (effectiveIsPinSet) {
                                                // Verify mode
                                                pin += digit
                                                if (pin.length >= 4) {
                                                    // Auto-submit length-match attempts
                                                    // UI should ideally have an 'enter' button for long PINs,
                                                    // but for UX we can let user press 'enter' or auto check
                                                }
                                            } else if (isConfirmMode) {
                                                confirmPin += digit
                                                if (confirmPin.length >= 4 && confirmPin == pin) {
                                                    justFinishedSetup = true
                                                    viewModel.setPin(pin)
                                                } else if (confirmPin.length == pin.length && confirmPin != pin) {
                                                    errorMessage = "PINs don't match. Try again."
                                                    confirmPin = ""
                                                }
                                            } else {
                                                pin += digit
                                            }
                                        }
                                    },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = digit,
                                        fontSize = if (digit == "⌫") 20.sp else 24.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Set PIN / Confirm / Verify button
            if (effectiveIsPinSet) {
               if (pin.length >= 4) {
                   Button(
                       onClick = { viewModel.verifyPin(pin) },
                       modifier = Modifier
                           .fillMaxWidth()
                           .height(52.dp),
                       shape = RoundedCornerShape(14.dp)
                   ) {
                       Text("Verify PIN", fontWeight = FontWeight.SemiBold)
                   }
               }
            } else if (!isConfirmMode && pin.length >= 4) {
                Button(
                    onClick = { isConfirmMode = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Confirm PIN", fontWeight = FontWeight.SemiBold)
                }
            }

            if (uiState.isPinSet && uiState.isSuccess) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF66BB6A).copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = Color(0xFF66BB6A))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("PIN is set. Settings are protected.",
                            color = Color(0xFF66BB6A), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Success Overlay
        if (showSuccessOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF66BB6A),
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (forceSetup) "PIN Changed!" else "PIN Set Successfully!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Your settings are now protected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
