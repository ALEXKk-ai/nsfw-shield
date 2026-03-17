package com.nsfwshield.app.ui.accountability

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nsfwshield.app.premium.AccountabilityReportService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountabilityScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccountabilityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showEmailDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accountability", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Premium badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF7C4DFF).copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Premium Feature Active",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF7C4DFF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Partner email section
            Text(
                "Accountability Partner",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your partner receives periodic reports with aggregated stats. Reports never contain URLs, images, or message content — only metadata.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (uiState.partnerEmail.isBlank()) "No partner set" else uiState.partnerEmail,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (uiState.partnerEmail.isBlank()) "Tap to add partner email"
                            else "Verified ✓",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.partnerEmail.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else Color(0xFF66BB6A)
                        )
                    }
                    TextButton(onClick = { showEmailDialog = true }) {
                        Text(if (uiState.partnerEmail.isBlank()) "Add" else "Change")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Report frequency
            Text("Report Frequency", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountabilityReportService.ReportPeriod.entries.forEach { period ->
                    val periodName = period.name.lowercase().replaceFirstChar { it.uppercase() }
                    FilterChip(
                        selected = uiState.reportFrequency == periodName,
                        onClick = { viewModel.updateReportFrequency(period) },
                        label = {
                            Text(periodName)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Protection Status & Delay
            Text("Security & Protection", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isProtectionActive) Icons.Default.Shield else Icons.Default.ShieldMoon,
                            contentDescription = null,
                            tint = if (uiState.isProtectionActive) Color(0xFF66BB6A) else Color(0xFFFFA726)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Protection Status", fontWeight = FontWeight.Bold)
                            Text(
                                if (uiState.isProtectionActive) "Active and Monitoring" else "Protection Paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (uiState.timeLeftToDisable != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEF5350).copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Deactivation Countdown",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFEF5350)
                                )
                                Text(
                                    uiState.timeLeftToDisable!!,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFEF5350)
                                )
                                Text(
                                    "You must wait 24 hours to disable protection.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFFEF5350).copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.cancelDisableRequest() }) {
                                    Text("Cancel Request", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    } else if (uiState.isProtectionActive) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.requestDisableProtection() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Request to Disable Protection")
                        }
                        Text(
                            "Triggering this will start a 24-hour countdown. Your partner will be notified.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Partner Revocation
            if (uiState.partnerEmail.isNotBlank()) {
                OutlinedButton(
                    onClick = { viewModel.revokePartner() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                ) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Revoke Accountability Partner")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Note: Your partner will be notified about the revocation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF5350).copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Email input dialog
    if (showEmailDialog) {
        var emailInput by remember { mutableStateOf(uiState.partnerEmail) }
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            title = { Text("Partner Email", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Email address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updatePartnerInfo(emailInput)
                        showEmailDialog = false
                    },
                    enabled = emailInput.contains("@")
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEmailDialog = false }) { Text("Cancel") }
            }
        )
    }
}
