package com.nsfwshield.app.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nsfwshield.app.ui.navigation.Routes
import com.nsfwshield.app.security.DeviceAdminReceiver

/**
 * Settings screen with general app settings, filter configuration,
 * and navigation to PIN lock and accountability features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPin: () -> Unit,
    onNavigateToAccountability: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToAdminDeactivation: () -> Unit,
    onNavigateToAdminManagement: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            viewModel.setAccessibilityEnabled(true)
        } else {
            // Revert toggle if permission denied
            viewModel.setAccessibilityEnabled(false)
        }
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // If they granted admin privileges
        val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            viewModel.setUninstallProtectionEnabled(true)
        } else {
            viewModel.setUninstallProtectionEnabled(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
            // ─── Protection Settings ───
            SettingsSectionHeader("Protection")

            SettingsToggle(
                icon = Icons.Default.VpnLock,
                title = "DNS Filtering (VPN)",
                subtitle = "Block explicit domains at network level",
                checked = uiState.vpnEnabled,
                onCheckedChange = { viewModel.setVpnEnabled(it) }
            )

            SettingsToggle(
                icon = Icons.Default.Visibility,
                title = "Screen Monitoring",
                subtitle = "Detect explicit content on screen",
                checked = uiState.accessibilityEnabled,
                onCheckedChange = { isChecked -> 
                    if (isChecked && !Settings.canDrawOverlays(context)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        overlayPermissionLauncher.launch(intent)
                    } else {
                        viewModel.setAccessibilityEnabled(isChecked)
                    }
                }
            )

            SettingsToggle(
                icon = Icons.Default.Search,
                title = "Safe Search",
                subtitle = "Enforce safe search on Google, YouTube, Bing",
                checked = uiState.safeSearchEnabled,
                onCheckedChange = { viewModel.setSafeSearchEnabled(it) }
            )

            SettingsToggle(
                icon = Icons.Default.PhotoLibrary,
                title = "Media Scanner",
                subtitle = "Scan gallery & downloads for explicit images",
                checked = uiState.mediaScanEnabled,
                onCheckedChange = { viewModel.setMediaScanEnabled(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Security Settings ───
            SettingsSectionHeader("Security")

            SettingsNavItem(
                icon = Icons.Default.Lock,
                title = "Admin PIN Lock",
                subtitle = "Protect settings with a PIN",
                onClick = { 
                    if (uiState.isPinSet) {
                        onNavigateToAdminManagement()
                    } else {
                        onNavigateToPin()
                    }
                }
            )

            SettingsNavItem(
                icon = Icons.Default.People,
                title = "Accountability Partner",
                subtitle = "Set up partner reports",
                onClick = onNavigateToAccountability,
                premiumBadge = true
            )

            SettingsToggle(
                icon = Icons.Default.AdminPanelSettings,
                title = "Uninstall Protection",
                subtitle = "Require PIN to uninstall app",
                checked = uiState.uninstallProtectionEnabled,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        // Request Device Admin (Turning ON)
                        val componentName = ComponentName(context, DeviceAdminReceiver::class.java)
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "NSFW Shield requires this to prevent unauthorized uninstallation.")
                        }
                        deviceAdminLauncher.launch(intent)
                    } else {
                        // Turning OFF
                        if (uiState.isPinSet) {
                            // Require PIN
                            onNavigateToAdminDeactivation()
                        } else {
                            viewModel.setUninstallProtectionEnabled(false)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Protection Updates ───
            SettingsSectionHeader("Protection Updates")

            SettingsToggle(
                icon = Icons.Default.CloudDownload,
                title = "Auto-Update Blocklist",
                subtitle = "Fetch new blocked domains every 24h",
                checked = uiState.autoUpdateBlocklist,
                onCheckedChange = { viewModel.setAutoUpdateBlocklist(it) }
            )

            SettingsToggle(
                icon = Icons.Default.Wifi,
                title = "Wi-Fi Only Updates",
                subtitle = "Only download updates on Wi-Fi",
                checked = uiState.updateOnlyOnWifi,
                onCheckedChange = { viewModel.setUpdateOnlyOnWifi(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── App Settings ───
            SettingsSectionHeader("App")

            SettingsToggle(
                icon = Icons.Default.DarkMode,
                title = "Dark Mode",
                subtitle = "Use dark theme",
                checked = uiState.darkMode,
                onCheckedChange = { viewModel.setDarkMode(it) }
            )

            SettingsToggle(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                subtitle = "Show blocking notifications",
                checked = uiState.notificationsEnabled,
                onCheckedChange = { viewModel.setNotificationsEnabled(it) }
            )

            SettingsNavItem(
                icon = Icons.Default.WorkspacePremium,
                title = "Subscription",
                subtitle = "Manage your plan",
                onClick = onNavigateToSubscription
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── About ───
            SettingsSectionHeader("About")

            SettingsNavItem(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "How we handle your data",
                onClick = onNavigateToPrivacy
            )

            SettingsNavItem(
                icon = Icons.Default.Info,
                title = "About NSFW Shield",
                subtitle = "Version 1.0.0",
                onClick = onNavigateToAbout
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    premiumBadge: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium)
                    if (premiumBadge) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF7C4DFF).copy(alpha = 0.15f)
                        ) {
                            Text("PRO", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7C4DFF), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
