package com.example.xpense.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.data.backup.AutoBackupFrequency
import com.example.xpense.data.backup.RestoreMode
import com.example.xpense.ui.components.ConfirmDialog
import com.example.xpense.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(viewModel: ExpenseViewModel) {
    val signedInEmail  by viewModel.signedInEmail.collectAsState()
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    val backupState    by viewModel.backupState.collectAsState()
    val autoFreq       by viewModel.autoBackupFrequency.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    // The Daily/Weekly/Monthly chooser is collapsible once auto-backup is on.
    var freqExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { viewModel.refreshBackupState() }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onDriveSignInResult(result.data) }

    // Surface success/error states as a snackbar, then reset to Idle.
    LaunchedEffect(backupState) {
        when (val s = backupState) {
            is BackupUiState.Success -> { snackbarHostState.showSnackbar(s.message); viewModel.clearBackupState() }
            is BackupUiState.Error   -> { snackbarHostState.showSnackbar(s.message); viewModel.clearBackupState() }
            else -> {}
        }
    }
    val isWorking = backupState is BackupUiState.Working

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Scaffold(
        containerColor = DarkBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Drive account card ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkCard)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(CategoryTravelColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudCircle, null, tint = CategoryTravelColor, modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Google Drive", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            signedInEmail ?: "Not connected",
                            color = if (signedInEmail != null) TextSecondary else TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    if (signedInEmail != null) {
                        TextButton(onClick = { viewModel.signOutDrive() }, enabled = !isWorking) {
                            Text("Disconnect", color = RedNegative, fontSize = 13.sp)
                        }
                    }
                }

                if (signedInEmail == null) {
                    GradientButton("Connect Google Drive", enabled = !isWorking) {
                        signInLauncher.launch(viewModel.driveSignInIntent())
                    }
                }
            }

            if (signedInEmail != null) {
                // ── Backup card ───────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkCard)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Backup", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        lastBackupTime?.let { "Last backed up: ${dateFmt.format(Date(it))}" }
                            ?: "No backup yet",
                        color = TextMuted, fontSize = 12.sp
                    )
                    GradientButton("Back up now", enabled = !isWorking) { viewModel.backupNow() }
                }

                // ── Automatic backup card ─────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkCard)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic backup", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (autoFreq == AutoBackupFrequency.OFF) "Automatic backup is off"
                                else "Backs up automatically around 2:00 AM",
                                color = TextMuted, fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = autoFreq != AutoBackupFrequency.OFF,
                            onCheckedChange = { on ->
                                viewModel.setAutoBackupFrequency(
                                    if (on) AutoBackupFrequency.DAILY else AutoBackupFrequency.OFF
                                )
                            },
                            enabled = !isWorking,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PurplePrimary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurface
                            )
                        )
                    }

                    if (autoFreq != AutoBackupFrequency.OFF) {
                        val freqLabel = when (autoFreq) {
                            AutoBackupFrequency.WEEKLY -> "Weekly"
                            AutoBackupFrequency.MONTHLY -> "Monthly"
                            else -> "Daily"
                        }
                        HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                        // Collapsible header: shows the current cadence; tap to reveal/hide options.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { freqExpanded = !freqExpanded }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Frequency", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(freqLabel, color = PurpleLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                if (freqExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (freqExpanded) "Collapse" else "Expand",
                                tint = TextMuted, modifier = Modifier.size(20.dp)
                            )
                        }
                        AnimatedVisibility(visible = freqExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FrequencyRow("Daily", autoFreq == AutoBackupFrequency.DAILY) {
                                    viewModel.setAutoBackupFrequency(AutoBackupFrequency.DAILY)
                                }
                                FrequencyRow("Weekly", autoFreq == AutoBackupFrequency.WEEKLY) {
                                    viewModel.setAutoBackupFrequency(AutoBackupFrequency.WEEKLY)
                                }
                                FrequencyRow("Monthly", autoFreq == AutoBackupFrequency.MONTHLY) {
                                    viewModel.setAutoBackupFrequency(AutoBackupFrequency.MONTHLY)
                                }
                            }
                        }
                    }
                }

                // ── Restore card ──────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkCard)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Restore", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Bring back your data from Google Drive on this or a new device.",
                        color = TextMuted, fontSize = 12.sp
                    )
                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        enabled = !isWorking && lastBackupTime != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PurplePrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PurpleLight)
                    ) { Text("Restore from Drive", fontWeight = FontWeight.Bold) }
                }
            }

            if (isWorking) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text((backupState as BackupUiState.Working).message, color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }

    // ── Restore mode chooser ──────────────────────────────────────────────────
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            containerColor = DarkCard,
            title = { Text("Restore from Drive", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RestoreOption(
                        title = "Merge",
                        subtitle = "Keep current data and add anything from the backup. Duplicates are skipped.",
                        onClick = {
                            showRestoreDialog = false
                            viewModel.restoreBackup(RestoreMode.MERGE)
                        }
                    )
                    RestoreOption(
                        title = "Replace",
                        subtitle = "Erase everything on this device and restore the backup exactly.",
                        destructive = true,
                        onClick = {
                            showRestoreDialog = false
                            confirmReplace = true
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    if (confirmReplace) {
        ConfirmDialog(
            title = "Replace all data?",
            message = "This erases every transaction, category and rule on this device, then restores the backup. This can't be undone.",
            confirmLabel = "Replace",
            onConfirm = {
                confirmReplace = false
                viewModel.restoreBackup(RestoreMode.REPLACE)
            },
            onDismiss = { confirmReplace = false }
        )
    }
}

@Composable
private fun RestoreOption(
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = if (destructive) RedNegative else PurpleLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun FrequencyRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = PurpleLight,
                unselectedColor = TextMuted
            )
        )
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun GradientButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) Brush.linearGradient(listOf(PurplePrimary, PurpleLight))
                else Brush.linearGradient(listOf(DarkSurface, DarkSurface))
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color.White else TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
