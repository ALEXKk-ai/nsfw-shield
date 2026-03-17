package com.nsfwshield.app.ui.pinlock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen that prompts for a PIN before navigating to a protected route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinPromptScreen(
    targetRoute: String,
    onNavigateBack: () -> Unit,
    onPinSuccess: (String) -> Unit,
    viewModel: PinLockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf(uiState.error) }
    val maxPinLength = 6

    // If no PIN is actually set, immediately succeed
    LaunchedEffect(uiState.isChecking, uiState.isPinSet) {
        if (!uiState.isChecking && !uiState.isPinSet) {
            onPinSuccess(targetRoute)
        }
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.resetSuccessState()
            onPinSuccess(targetRoute)
        }
    }

    LaunchedEffect(uiState.error) {
        errorMessage = uiState.error
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unlock Required", fontWeight = FontWeight.Bold) },
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
            Surface(
                shape = CircleShape,
                color = Color(0xFFEF5350).copy(alpha = 0.12f),
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.padding(20.dp).fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Enter Admin PIN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "This area is protected by an Admin PIN.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                repeat(maxPinLength) { index ->
                    Surface(
                        shape = CircleShape,
                        color = if (index < pin.length) Color(0xFF1A73E8) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp).size(16.dp)
                    ) {}
                }
            }

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(32.dp))

            val numbers = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            )

            numbers.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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
                                        errorMessage = null
                                        if (digit == "⌫") {
                                            if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        } else if (pin.length < maxPinLength) {
                                            pin += digit
                                            if (pin.length >= 4) {
                                                // auto-verify on 4th digit for better UX, or they can keep typing if 6
                                                // We rely on the button for explicit verify if length > 4 or if auto-verify fails
                                            }
                                        }
                                    },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = digit, fontSize = if (digit == "⌫") 20.sp else 24.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (pin.length >= 4) {
                Button(
                    onClick = { viewModel.verifyPin(pin) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Unlock", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
