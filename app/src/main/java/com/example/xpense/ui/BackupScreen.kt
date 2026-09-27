package com.example.xpense.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xpense.data.backup.AutoBackupFrequency
import com.example.xpense.data.backup.RestoreMode
import com.example.xpense.ui.components.ConfirmDialog
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BackupScreen(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
    val signedInEmail  by viewModel.signedInEmail.collectAsState()
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    val backupState    by viewModel.backupState.collectAsState()
    val autoFreq       by viewModel.autoBackupFrequency.collectAsState()
    val toast = LocalToast.current

    var showRestoreDialog by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    // The frequency chooser starts collapsed on every visit and only opens on tap.
    var freqExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshBackupState() }
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onDriveSignInResult(result.data)
    }
    // Surface success/error states as a toast, then reset to Idle.
    LaunchedEffect(backupState) {
        when (val s = backupState) {
            is BackupUiState.Success -> { toast.show(s.message); viewModel.clearBackupState() }
            is BackupUiState.Error -> { toast.show(s.message); viewModel.clearBackupState() }
            else -> {}
        }
    }
    val working = backupState as? BackupUiState.Working
    val isWorking = working != null
    val connected = signedInEmail != null
    val sky = Color(0xFF38BDF8)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("Backup & restore", { viewModel.navigateBack() }) }

        // ── Drive account ─────────────────────────────────────────────────
        item {
            GlassCard(Modifier.padding(top = 6.dp).fillMaxWidth(), radius = 22.dp, contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(Icons.Rounded.Cloud, sky, size = 46.dp, radius = 15.dp, iconSize = 23.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Google Drive", style = XType.bodyStrong, color = c.tx)
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (connected) c.pos else c.tx3))
                        }
                        Text(signedInEmail ?: "Not connected", style = XType.caption, color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text(
                        if (connected) "Sign out" else "Connect",
                        style = XType.captionStrong, color = if (connected) c.neg else c.ac,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.card2)
                            .clickable(enabled = !isWorking) {
                                if (connected) viewModel.signOutDrive() else signInLauncher.launch(viewModel.driveSignInIntent())
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (connected) {
            // ── Backup ────────────────────────────────────────────────────
            item {
                GlassCard(
                    Modifier.fillMaxWidth(), radius = 22.dp, contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    decoration = Modifier.cornerGlow(c.ac, 0.25f, 110.dp) { w, h, _ -> Offset(w - 30f, h + 40f) }
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Backup", style = XType.section, color = c.tx)
                            Text("Last backed up", style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 4.dp))
                            Text(
                                lastBackupTime?.let { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(it)) } ?: "Never",
                                style = XType.monoS.copy(fontSize = XType.small.fontSize), color = c.tx, modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        XIcon(if (lastBackupTime != null) Icons.Rounded.Verified else Icons.Rounded.CloudOff, 22.dp, if (lastBackupTime != null) c.pos else c.tx3)
                    }
                    AnimatedVisibility(isWorking) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val t = rememberInfiniteTransition(label = "busy")
                            val p by t.animateFloat(0.1f, 1f, infiniteRepeatable(tween(1600)), label = "p")
                            ProgressBar(p, brush = Brush.horizontalGradient(listOf(c.ac, c.ac2)), height = 6.dp)
                            Text(working?.message ?: "", style = XType.caption, color = c.tx2)
                        }
                    }
                    XButton(if (isWorking) "Working…" else "Back up now", { viewModel.backupNow() }, Modifier.fillMaxWidth(), enabled = !isWorking, icon = Icons.Rounded.CloudUpload, height = 52.dp)
                }
            }

            // ── Automatic backup ──────────────────────────────────────────
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 22.dp, contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Automatic backup", style = XType.section, color = c.tx)
                            Text(if (autoFreq == AutoBackupFrequency.OFF) "Off" else "Runs around 2:00 am", style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 3.dp))
                        }
                        XSwitch(autoFreq != AutoBackupFrequency.OFF) { on ->
                            if (!isWorking) viewModel.setAutoBackupFrequency(if (on) AutoBackupFrequency.DAILY else AutoBackupFrequency.OFF)
                        }
                    }
                    if (autoFreq != AutoBackupFrequency.OFF) {
                        Box(Modifier.padding(top = 16.dp, bottom = 4.dp).fillMaxWidth().height(1.dp).background(c.line))
                        val rot by animateFloatAsState(if (freqExpanded) 180f else 0f, label = "rot")
                        Row(Modifier.fillMaxWidth().clickable { freqExpanded = !freqExpanded }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Frequency", style = XType.body, color = c.tx2, modifier = Modifier.weight(1f))
                            Text(freqLabel(autoFreq), style = XType.bodyStrong, color = c.ac)
                            XIcon(Icons.Rounded.ExpandMore, 20.dp, c.ac, Modifier.padding(start = 4.dp).rotate(rot))
                        }
                        AnimatedVisibility(freqExpanded) {
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    AutoBackupFrequency.DAILY to Icons.Rounded.Today,
                                    AutoBackupFrequency.WEEKLY to Icons.Rounded.DateRange,
                                    AutoBackupFrequency.MONTHLY to Icons.Rounded.CalendarMonth
                                ).forEach { (f, icon) -> FreqTile(freqLabel(f), icon, autoFreq == f, Modifier.weight(1f)) { viewModel.setAutoBackupFrequency(f) } }
                            }
                        }
                    }
                }
            }

            // ── Restore ───────────────────────────────────────────────────
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 22.dp, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Text("Restore", style = XType.section, color = c.tx)
                        Text("Bring back your data from Google Drive on this or a new device.", style = XType.small, color = c.tx2, modifier = Modifier.padding(top = 4.dp))
                    }
                    XButton("Restore from Drive", { showRestoreDialog = true }, Modifier.fillMaxWidth(), style = BtnStyle.Outline, enabled = !isWorking && lastBackupTime != null, icon = Icons.Rounded.CloudDownload)
                }
            }
        } else {
            item {
                EmptyState(Icons.Rounded.CloudSync, "Keep your data safe", "Connect Google Drive to back up transactions, categories and rules.", actionText = "Connect Google Drive", onAction = { signInLauncher.launch(viewModel.driveSignInIntent()) })
            }
        }
    }

    if (showRestoreDialog) {
        XDialog({ showRestoreDialog = false }) {
            DialogBadge(Icons.Rounded.CloudDownload)
            DialogTitle("Restore from Drive", "How should the backup be combined with what's on this device?")
            RestoreOption("Merge", "Keep current data and add anything from the backup. Duplicates are skipped.", Icons.Rounded.Merge, false) {
                showRestoreDialog = false
                viewModel.restoreBackup(RestoreMode.MERGE)
            }
            RestoreOption("Replace", "Erase everything on this device and restore the backup exactly.", Icons.Rounded.SwapHoriz, true) {
                showRestoreDialog = false
                confirmReplace = true
            }
            XButton("Cancel", { showRestoreDialog = false }, Modifier.fillMaxWidth(), style = BtnStyle.Secondary)
        }
    }
    if (confirmReplace) {
        ConfirmDialog(
            title = "Replace all data?",
            message = "This erases every transaction, category and rule on this device, then restores the backup. This can't be undone.",
            confirmLabel = "Replace",
            onConfirm = { confirmReplace = false; viewModel.restoreBackup(RestoreMode.REPLACE) },
            onDismiss = { confirmReplace = false }
        )
    }
}

private fun freqLabel(f: AutoBackupFrequency) = when (f) {
    AutoBackupFrequency.WEEKLY -> "Weekly"
    AutoBackupFrequency.MONTHLY -> "Monthly"
    AutoBackupFrequency.OFF -> "Off"
    else -> "Daily"
}

@Composable
private fun FreqTile(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(if (selected) c.acSoft else c.card2).border(1.dp, if (selected) c.ac else c.line, shape)
            .clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        XIcon(icon, 20.dp, if (selected) c.ac else c.tx2)
        Text(label, style = XType.smallStrong, color = c.tx)
    }
}

@Composable
private fun RestoreOption(title: String, subtitle: String, icon: ImageVector, destructive: Boolean, onClick: () -> Unit) {
    val c = XpenseTheme.colors
    val tint = if (destructive) c.neg else c.ac
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card2).clickable(onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(icon, tint, size = 36.dp, radius = 12.dp, iconSize = 19.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = XType.bodyStrong, color = tint)
            Text(subtitle, style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
