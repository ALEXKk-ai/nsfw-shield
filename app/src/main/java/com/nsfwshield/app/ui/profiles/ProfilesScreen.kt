package com.nsfwshield.app.ui.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nsfwshield.app.core.profiles.FilterProfile
import com.nsfwshield.app.core.profiles.SensitivityLevel
import com.nsfwshield.app.data.BlockedDomain
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Profile management screen.
 * Lists all filter profiles with add/edit/delete and active profile selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPremium = uiState.isPremium
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showUpsellDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<FilterProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<FilterProfile?>(null) }
    var beamProfile by remember { mutableStateOf<FilterProfile?>(null) }
    var updatingProfile by remember { mutableStateOf<FilterProfile?>(null) }
    var actionToVerify by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                // Should show error, but we'll let user try again or see the dialog blank
            }
        }
    )
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filter Profiles", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        if (isPremium) {
                            if (viewModel.isPinSet()) {
                                actionToVerify = { showImportDialog = true }
                            } else {
                                showImportDialog = true
                            }
                        } else {
                            showUpsellDialog = true
                        }
                    }) {
                        Icon(Icons.Default.Input, contentDescription = "Import Profile")
                    }
                    Box {
                        IconButton(onClick = { 
                            if (isPremium) showCreateDialog = true else showUpsellDialog = true 
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Profile")
                        }
                        if (!isPremium) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color(0xFF7C4DFF),
                                modifier = Modifier
                                    .size(14.dp)
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Managed Shields (Parent/Sender View)
            item {
                Text(
                    "Managed Shields",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7C4DFF),
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                Text(
                    "Profiles you created to manage other devices.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    "Edit settings below, then tap 'Sync' to push updates via QR.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7C4DFF).copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(uiState.managedShields) { profile ->
                ProfileCard(
                    profile = profile,
                    managedBy = null,
                    onActivate = { /* Cannot activate sender profiles */ },
                    onEdit = {
                        if (viewModel.isPinSet()) {
                            actionToVerify = { editingProfile = profile }
                        } else {
                            editingProfile = profile
                        }
                    },
                    onDelete = { 
                        if (profile.isFamilyShield && !profile.isLinked) {
                            // Unlinked shields show simple confirmation
                            profileToDelete = profile
                        } else {
                            // Linked shields or personal profiles
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                viewModel.initiateDeleteProfile(profile)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    onBeam = { 
                        if (isPremium) beamProfile = profile else showUpsellDialog = true
                    }
                )
            }

            // Managed Strategy Override Banner
            val activeManagedStrategy = uiState.managedStrategies.find { it.isActive }
            if (activeManagedStrategy != null) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Managed Strategy Active",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Rules from '${activeManagedStrategy.displayName}' are currently overriding your personal settings.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // 2. Personal Selection (My Profiles)
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Personal Selection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "Your local safety configuration",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            val activeShieldName = uiState.managedStrategies.find { it.isActive }?.displayName

            items(uiState.personalProfiles) { profile ->
                ProfileCard(
                    profile = profile,
                    managedBy = activeShieldName,
                    onActivate = {
                        if (viewModel.isPinSet()) {
                            actionToVerify = { viewModel.activateProfile(profile.profileId) }
                        } else {
                            viewModel.activateProfile(profile.profileId)
                        }
                    },
                    onEdit = {
                        if (viewModel.isPinSet()) {
                            actionToVerify = { editingProfile = profile }
                        } else {
                            editingProfile = profile
                        }
                    },
                    onDelete = {
                        if (viewModel.isPinSet()) {
                            actionToVerify = { viewModel.initiateDeleteProfile(profile) }
                        } else {
                            viewModel.initiateDeleteProfile(profile)
                        }
                    },
                    onBeam = null // Personal profiles cannot be beamed
                )
            }

            // 2. Managed Strategies Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Managed Strategy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7C4DFF),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "A single source of truth for your protection. New scans replace the current strategy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            if (uiState.managedStrategies.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No Managed Strategy active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                val strategy = uiState.managedStrategies.first()
                item {
                    ProfileCard(
                        profile = strategy,
                        managedBy = null,
                        onActivate = {
                            if (viewModel.isPinSet()) {
                                actionToVerify = { viewModel.activateProfile(strategy.profileId) }
                            } else {
                                viewModel.activateProfile(strategy.profileId)
                            }
                        },
                        onEdit = { /* Recipient cannot edit managed strategies */ },
                        onDelete = { 
                            if (strategy.isFamilyShield && !strategy.isLinked) {
                                profileToDelete = strategy
                            } else {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.initiateDeleteProfile(strategy)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        },
                        onBeam = { 
                            // Beam for a managed strategy is "Receive Update" (Recipient View)
                            if (isPremium) updatingProfile = strategy else showUpsellDialog = true
                        }
                    )
                }
            }

            if (!uiState.isPremium && (uiState.personalProfiles.size + uiState.managedStrategies.size) < 2) {
                item {
                    LockedSlotItem(onClick = onNavigateToSubscription)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
            
            // Custom Blocked Sites Section (Premium)
            item {
                Text(
                    "Custom Blocked Sites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                var newDomain by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newDomain,
                        onValueChange = { newDomain = it },
                        placeholder = { Text(if (isPremium) "e.g. example.com" else "Premium Feature") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = isPremium,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newDomain.isNotBlank() && isPremium) {
                                viewModel.addCustomDomain(newDomain.trim())
                                newDomain = ""
                            } else if (!isPremium) {
                                showUpsellDialog = true
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isPremium) MaterialTheme.colorScheme.primaryContainer 
                                           else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            if (isPremium) Icons.Default.Add else Icons.Default.Lock, 
                            contentDescription = "Add Domain"
                        )
                    }
                }
            }
            
            items(uiState.customDomains) { domainEntry ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(domainEntry.domain, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { viewModel.removeCustomDomain(domainEntry.domain) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showCreateDialog) {
        CreateProfileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, sensitivity, isFamily ->
                viewModel.createProfile(name, sensitivity, isFamily)
                showCreateDialog = false
            }
        )
    }

    if (showUpsellDialog) {
        PremiumProfilesUpsellDialog(
            onDismiss = { showUpsellDialog = false },
            onUpgrade = {
                showUpsellDialog = false
                onNavigateToSubscription()
            }
        )
    }

    editingProfile?.let { profile ->
        EditProfileDialog(
            profile = profile,
            onDismiss = { editingProfile = null },
            onSave = { updatedProfile ->
                viewModel.updateProfile(updatedProfile)
                editingProfile = null
            }
        )
    }

    val customDomainsByViewModel by viewModel.customDomains.collectAsState()
    
    beamProfile?.let { profile ->
        BeamProfileDialog(
            profile = profile,
            customDomains = customDomainsByViewModel.map { it.domain },
            onDismiss = { beamProfile = null },
            onConfirmLink = {
                viewModel.markProfileAsLinked(profile.profileId)
            }
        )
    }

    if (showImportDialog || updatingProfile != null) {
        ImportProfileDialog(
            title = if (updatingProfile != null) "Receive Update" else "Import Shield",
            description = if (updatingProfile != null) "Scan the updated 'Beam QR' for '${updatingProfile?.displayName}'." else "Scan a 'Beam QR' from another device to copy its safety rules.",
            onDismiss = { 
                showImportDialog = false
                updatingProfile = null
            },
            onImport = { code ->
                if (updatingProfile != null) {
                    viewModel.importProfileFromBeamCode(code)
                    updatingProfile = null
                } else {
                    viewModel.importProfileFromBeamCode(code)
                    showImportDialog = false
                }
            }
        )
    }

    uiState.pendingHandshakeProfile?.let { profile ->
        HandshakeHubDialog(
            profile = profile,
            authCode = viewModel.getDeletionAuthorizationCode(profile),
            errorMessage = uiState.errorMessage,
            onDismiss = { viewModel.cancelHandshakeDeletion() },
            onScanResult = { code: String ->
                viewModel.completeHandshakeDeletion(code)
            }
        )
    }

    actionToVerify?.let { action ->
        PinVerificationDialog(
            onDismiss = { actionToVerify = null },
            onVerified = {
                action()
                actionToVerify = null
            },
            viewModel = viewModel
        )
    }

    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Shield?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${profile.displayName}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.initiateDeleteProfile(profile)
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PinVerificationDialog(
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    viewModel: ProfilesViewModel
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Admin PIN Required", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Please enter your admin PIN to confirm this action.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { 
                        if (it.length <= 8) {
                            pin = it
                            error = false
                        }
                    },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.width(120.dp),
                    isError = error,
                    singleLine = true,
                    placeholder = { Text("PIN", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                )
                if (error) {
                    Text("Invalid PIN", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (viewModel.verifyPin(pin)) {
                        onVerified()
                    } else {
                        error = true
                    }
                },
                enabled = pin.length >= 4
            ) {
                Text("Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

  @Composable
private fun ProfileCard(
    profile: FilterProfile,
    managedBy: String?,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBeam: (() -> Unit)?
) {
    val sensitivityColor = when (profile.sensitivity) {
        SensitivityLevel.STRICT -> Color(0xFFEF5350)
        SensitivityLevel.MODERATE -> Color(0xFF1A73E8)
        SensitivityLevel.CUSTOM -> Color(0xFF7C4DFF)
    }
    
    val icon = if (profile.isFamilyShield) Icons.Default.Shield else Icons.Default.Person
    val isManaged = managedBy != null

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (profile.isActive) 4.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = sensitivityColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = sensitivityColor,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            profile.displayName,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${profile.sensitivity.name} • Threshold: ${(profile.confidenceThreshold * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (profile.isFamilyShield && profile.isLinked) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFF7C4DFF).copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "LINKED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF7C4DFF),
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (profile.isActive || (profile.isActive && isManaged)) {
                    val statusText = when {
                        profile.isFamilyShield -> "Enforcing"
                        isManaged -> "Managed"
                        else -> "Active"
                    }
                    val statusColor = when {
                        profile.isFamilyShield -> Color(0xFF7C4DFF)
                        isManaged -> Color(0xFF78909C)
                        else -> Color(0xFF66BB6A)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            statusText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category toggles summary
            val activeCategories = mutableListOf<String>()
            if (profile.explicitSexual) activeCategories.add("Sexual")
            if (profile.violenceGore) activeCategories.add("Violence")
            if (profile.hateSpeech) activeCategories.add("Hate Speech")
            if (profile.drugReferences) activeCategories.add("Drugs")
            if (profile.gambling) activeCategories.add("Gambling")
            if (profile.selfHarm) activeCategories.add("Self-Harm")

            Text(
                "Filtering: ${activeCategories.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!profile.isActive && !isManaged && !profile.isSender) {
                    TextButton(onClick = onActivate) {
                        Text("Set Active")
                    }
                }
                
                if (!profile.isFamilyShield || profile.isSender) {
                    TextButton(onClick = onEdit) {
                        Text("Edit")
                    }
                }

                if (onBeam != null) {
                    if (profile.isSender) {
                        TextButton(
                            onClick = { onBeam.invoke() },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Updates", style = MaterialTheme.typography.labelLarge)
                        }
                    } else if (profile.isFamilyShield) {
                        // Managed Strategy "Receive Update" button
                        TextButton(
                            onClick = { onBeam.invoke() },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Receive Update", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        IconButton(onClick = { onBeam.invoke() }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Beam (Share)",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (profile.isFamilyShield) {
                    TextButton(onClick = onDelete) {
                        val deleteText = when {
                            !profile.isLinked -> "Delete"
                            profile.isSender -> "Retire"
                            profile.isFamilyShield -> "Terminate"
                            else -> "Delete"
                        }
                        Text(deleteText, color = Color(0xFFEF5350))
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, SensitivityLevel, Boolean) -> Unit
) {
    val hasPersonal = (hiltViewModel<ProfilesViewModel>()).uiState.collectAsState().value.personalProfiles.isNotEmpty()
    var name by remember { mutableStateOf("") }
    var sensitivity by remember { mutableStateOf(SensitivityLevel.MODERATE) }
    var isFamilyShield by remember { mutableStateOf(hasPersonal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Managed Shield", fontWeight = FontWeight.SemiBold)
                        Text("For someone else (locked in managed list)", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(
                        checked = isFamilyShield, 
                        onCheckedChange = { if (!hasPersonal) isFamilyShield = it },
                        enabled = !hasPersonal
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, sensitivity, isFamilyShield) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditProfileDialog(
    profile: FilterProfile,
    onDismiss: () -> Unit,
    onSave: (FilterProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.displayName) }
    var explicitSexual by remember { mutableStateOf(profile.explicitSexual) }
    var violenceGore by remember { mutableStateOf(profile.violenceGore) }
    var hateSpeech by remember { mutableStateOf(profile.hateSpeech) }
    var drugReferences by remember { mutableStateOf(profile.drugReferences) }
    var gambling by remember { mutableStateOf(profile.gambling) }
    var selfHarm by remember { mutableStateOf(profile.selfHarm) }
    var delayToDisable by remember { mutableStateOf(profile.delayToDisableEnabled) }
    var threshold by remember { mutableStateOf(profile.confidenceThreshold) }
    var sensitivity by remember { mutableStateOf(profile.sensitivity) }
    
    val isPremium = (hiltViewModel<ProfilesViewModel>()).uiState.collectAsState().value.isPremium
    val isRecipient = profile.isFamilyShield && !profile.isSender

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Profile Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isRecipient
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Content Categories", fontWeight = FontWeight.SemiBold, 
                        color = if (isPremium) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    CategoryToggleItem("Explicit Sexual", explicitSexual, isPremium && !isRecipient) { explicitSexual = it }
                    CategoryToggleItem("Violence & Gore", violenceGore, isPremium && !isRecipient) { violenceGore = it }
                    CategoryToggleItem("Hate Speech", hateSpeech, isPremium && !isRecipient) { hateSpeech = it }
                    CategoryToggleItem("Drugs", drugReferences, isPremium && !isRecipient) { drugReferences = it }
                    CategoryToggleItem("Gambling", gambling, isPremium && !isRecipient) { gambling = it }
                    CategoryToggleItem("Self-Harm", selfHarm, isPremium && !isRecipient) { selfHarm = it }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (profile.sensitivity == SensitivityLevel.CUSTOM) {
                        Text("Sensitivity Threshold: ${(threshold * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = threshold,
                            onValueChange = { threshold = it },
                            valueRange = 0.5f..0.99f
                        )
                        
                        if (isPremium) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Policy Strategy", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            SensitivityLevel.entries.forEach { level ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = !isRecipient) { sensitivity = level }
                                ) {
                                    RadioButton(
                                        selected = sensitivity == level,
                                        onClick = { sensitivity = level },
                                        enabled = !isRecipient
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(level.name.lowercase().replaceFirstChar { it.uppercase() })
                                }
                            }
                            
                            LaunchedEffect(sensitivity) {
                                if (sensitivity != profile.sensitivity) {
                                    threshold = when (sensitivity) {
                                        SensitivityLevel.STRICT -> 0.70f
                                        SensitivityLevel.MODERATE -> 0.80f
                                        SensitivityLevel.CUSTOM -> threshold
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("24h Delay to Disable", fontWeight = FontWeight.SemiBold,
                                color = if (isPremium) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("The 'Master Lock' for experts", style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = delayToDisable, onCheckedChange = { delayToDisable = it }, enabled = isPremium && !isRecipient)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!isRecipient) {
                    onSave(profile.copy(
                        displayName = name,
                        sensitivity = sensitivity,
                        explicitSexual = explicitSexual,
                        violenceGore = violenceGore,
                        hateSpeech = hateSpeech,
                        drugReferences = drugReferences,
                        gambling = gambling,
                        selfHarm = selfHarm,
                        delayToDisableEnabled = delayToDisable,
                        confidenceThreshold = threshold
                    ))
                } else {
                    onDismiss()
                }
            }) {
                Text(if (isRecipient) "Close" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CategoryToggleItem(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun LockedSlotItem(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "Family Shield Slot",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Protect another family member with Premium",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7C4DFF).copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = Color(0xFF7C4DFF).copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PremiumProfilesUpsellDialog(
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = Color(0xFF7C4DFF),
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                "Unlock Family Profiles",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Create multiple profiles with custom sensitivity levels for each family member. Shield your entire household with Premium.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onUpgrade,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) {
                Text("Upgrade Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    )
}

@Composable
private fun BeamProfileDialog(
    profile: FilterProfile,
    customDomains: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirmLink: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val beamCode = remember(profile, customDomains) { 
        with(FilterProfile.Companion) { profile.toBeamCode(customDomains) }
    }
    val qrBitmap = remember(beamCode) { QrGenerator.generateQrCode(beamCode) }

    val isUpdate = profile.isSender && profile.isFamilyShield
    val title = if (isUpdate) "Sync Managed Shield" else "Beam Shield Configuration"
    val instruction = if (isUpdate) {
        "Show this QR code to the recipient's device to instantly sync your new safety configuration for '${profile.displayName}'."
    } else {
        "Show this QR code to your family members to instantly copy the '${profile.displayName}' safety rules to their devices."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { 
            Icon(
                if (isUpdate) Icons.Default.Sync else Icons.Default.QrCode, 
                contentDescription = null, 
                tint = if (isUpdate) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.primary
            ) 
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    instruction,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                
                qrBitmap?.let { bitmap ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(200.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(beamCode))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Share Code")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Identity stays private. Rules are copied locally.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Row {
                if (profile.isFamilyShield && !profile.isLinked) {
                    Button(
                        onClick = {
                            onConfirmLink()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C4DFF)
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm Link")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { 
                    Text(if (profile.isFamilyShield && !profile.isLinked) "Cancel" else "Done") 
                }
            }
        }
    )
}

@Composable
private fun ImportProfileDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    val context = LocalContext.current
    var showManualEntry by remember { mutableStateOf(false) }
    var manualCode by remember { mutableStateOf("") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            var isProcessing by remember { mutableStateOf(false) }
            val handleImport: (String) -> Unit = { code ->
                if (!isProcessing) {
                    isProcessing = true
                    onImport(code)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (showManualEntry) {
                    Text(
                        "Enter the Beam Code manually to copy safety rules.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        label = { Text("Beam Code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("V2|...") },
                        enabled = !isProcessing
                    )
                } else if (hasCameraPermission) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        if (!isProcessing) {
                            CameraScanner(onCodeScanned = handleImport)
                        }
                    }
                } else {
                    Text(
                        "Camera permission is required to scan QR codes from other family devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = { showManualEntry = !showManualEntry },
                    enabled = !isProcessing
                ) {
                    Text(if (showManualEntry) "Use Camera" else "Enter Code Manually")
                }
            }
        },
        confirmButton = {
            Row {
                if (showManualEntry) {
                    Button(
                        onClick = { 
                            if (manualCode.isNotBlank()) {
                                // We can use direct manualCode here since the dialog will close
                                onImport(manualCode.trim())
                            }
                        },
                        enabled = manualCode.isNotBlank()
                    ) {
                        Text("Import")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun CameraScanner(onCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val scannerOptions = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                    .build()
                val scanner = BarcodeScanning.getClient(scannerOptions)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            barcode.rawValue?.let { onCodeScanned(it) }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun HandshakeHubDialog(
    profile: FilterProfile,
    authCode: String,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onScanResult: (String) -> Unit
) {
    var mode by remember { mutableStateOf(0) } // 0: Select, 1: Scan, 2: Show QR, 3: Manual
    var manualCode by remember { mutableStateOf("") }
    val title = if (profile.isSender) "Security Handshake (Retire)" else "Security Handshake (Terminate)"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()
                    ) {
                        Text(
                            errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                
                when (mode) {
                    0 -> {
                        Text(
                            "Managed Profiles require a physical handshake to remove. Choose your role below.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Button(
                            onClick = { mode = 1 },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("I will Scan the code")
                        }
                        
                        OutlinedButton(
                            onClick = { mode = 2 },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("I will Show my code")
                        }

                        TextButton(
                            onClick = { mode = 3 },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Enter Code Manually")
                        }
                    }
                    1 -> {
                        val scanInstruction = if (profile.isSender) {
                            "Scan the 'Retirement Key' from the child's phone."
                        } else {
                            "Scan the 'Deletion Approval' from the parent's phone."
                        }
                        Text(scanInstruction, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            CameraScanner(onCodeScanned = onScanResult)
                        }
                    }
                    2 -> {
                        val qrLabel = if (profile.isSender) "Deletion Approval" else "Retirement Key"
                        Text(qrLabel, fontWeight = FontWeight.SemiBold, color = Color(0xFF7C4DFF))
                        Spacer(modifier = Modifier.height(8.dp))
                        val qrBitmap = remember(authCode) { QrGenerator.generateQrCode(authCode, 300) }
                        qrBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(220.dp).padding(12.dp)
                            )
                        }
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        
                        Text(
                            "Let the other device scan this code to proceed.",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { 
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(authCode))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Handshake Code")
                        }
                    }
                    3 -> {
                        Text(
                            "Enter the Handshake code exactly as shown on the other device.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        OutlinedTextField(
                            value = manualCode,
                            onValueChange = { manualCode = it },
                            label = { Text("Handshake Code") },
                            placeholder = { Text("DELETE_ACTION|...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onScanResult(manualCode.trim()) },
                            enabled = manualCode.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Confirm Handshake")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = if (mode == 0) onDismiss else { { mode = 0 } }) {
                Text(if (mode == 0) "Cancel" else "Back")
            }
        }
    )
}
