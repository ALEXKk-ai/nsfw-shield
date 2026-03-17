package com.nsfwshield.app.ui.info

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                "Last Updated: March 2026",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("Overview")
            Text("NSFW Shield is a private, on-device content filtering application. We believe your protection shouldn't come at the cost of your privacy.")
            
            SectionTitle("On-Device Processing")
            Text("All AI analysis, screen monitoring, and DNS filtering happens entirely on your phone. No images, screenshots, or personal data are ever sent to external servers.")
            
            SectionTitle("Data Collection")
            Text("The app collects zero personal data. Local logs are stored in an encrypted database for your personal viewing and are never shared. Accountability reports, if enabled, contain only metadata counts.")
            
            SectionTitle("Permissions")
            Text("Accessibility and VPN permissions are used exclusively for on-device content detection and DNS-level blocking. They are never used to track your behavior.")
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About NSFW Shield") },
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionTitle("Our Mission")
            Text("NSFW Shield was built to provide high-performance, real-time protection against explicit content without sacrificing privacy. Unlike other filters, we don't route your traffic through slow external servers.")
            
            SectionTitle("How It Works")
            Text("Using advanced machine learning, the app identifies explicit visual and textual patterns as they appear on your screen. Our local DNS engine blocks millions of adult domains instantly at the network level.")
            
            SectionTitle("Key Features")
            Text("• Real-time Screen Blurring\n• Global DNS Filtering\n• Safe Search Enforcement\n• Media Gallery Scanning\n• Uninstall Protection")
            
            SectionTitle("Version")
            Text("1.0.0 (Gold Master)")
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary
    )
}
