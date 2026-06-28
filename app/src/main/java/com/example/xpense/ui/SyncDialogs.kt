package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.xpense.ui.theme.*

/**
 * Sync confirm / progress / completion dialogs, hoisted out of any single screen so the
 * flow works no matter which screen triggered it (Home top-bar icon, Profile menu, …).
 * Render this once, above all screens, from MainActivity.
 */
@Composable
fun SyncDialogs(viewModel: ExpenseViewModel) {
    val showSyncConfirm by viewModel.showSyncConfirm.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    // ── Confirm ───────────────────────────────────────────────────────────
    if (showSyncConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.hideSyncConfirm() },
            containerColor = DarkCard,
            title = { Text("Confirm Sync", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Scan your SMS inbox (last 6 months) for bank transactions?", color = TextSecondary) },
            // Both actions live in the confirm slot as a full-width 50/50 row so Cancel and Sync
            // get equal space, instead of the default end-hugging layout where their widths differ.
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = { viewModel.hideSyncConfirm() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel", color = TextSecondary) }
                    Button(
                        onClick = { viewModel.confirmSyncAndStart() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) { Text("Yes, Sync Now", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
                }
            }
        )
    }

    // ── Progress ──────────────────────────────────────────────────────────
    // Capture into a local val: the progress indicator reads this in the draw phase,
    // so referencing the StateFlow's nullable value directly (with !!) crashes when it
    // flips to null at completion. A captured non-null float can't be nulled out underneath us.
    val progress = syncProgress
    if (progress != null) {
        Dialog(onDismissRequest = {}) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkCard)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    color = PurplePrimary,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp
                )
                Text("Scanning Messages…", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${(progress * 100).toInt()}% complete", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }

    // ── Completion message ────────────────────────────────────────────────
    val message = syncMessage
    if (message != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearSyncMessage() },
            containerColor = DarkCard,
            title = { Text("Sync Complete", color = TextPrimary) },
            text = { Text(message, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSyncMessage() }) {
                    Text("OK", color = PurpleLight, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
