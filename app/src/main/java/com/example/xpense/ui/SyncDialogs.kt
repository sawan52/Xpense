package com.example.xpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme

/**
 * Sync confirm / progress / completion dialogs, hoisted out of any single screen so the
 * flow works no matter which screen triggered it. Render this once, above all screens, from
 * MainActivity.
 */
@Composable
fun SyncDialogs(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
    val showSyncConfirm by viewModel.showSyncConfirm.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    if (showSyncConfirm) {
        XDialog({ viewModel.hideSyncConfirm() }) {
            DialogBadge(Icons.Rounded.Sms)
            DialogTitle("Sync SMS history?", "We'll scan the last 6 months of your SMS inbox for bank transactions. Messages never leave your phone.")
            DialogButtons("Yes, sync now", { viewModel.confirmSyncAndStart() }, { viewModel.hideSyncConfirm() })
        }
    }

    // Capture into a local val: the progress bar reads this in the draw phase, so referencing the
    // StateFlow's nullable value directly would crash when it flips to null at completion.
    val progress = syncProgress
    if (progress != null) {
        XDialog({}) {
            DialogBadge(Icons.Rounded.Sms)
            DialogTitle("Scanning messages…", "This only takes a moment.")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressBar(progress, brush = Brush.horizontalGradient(listOf(c.ac, c.ac2)), height = 6.dp)
                Text("${(progress * 100).toInt()}% complete", style = XType.monoS, color = c.tx2)
            }
        }
    }

    val message = syncMessage
    if (message != null) {
        XDialog({ viewModel.clearSyncMessage() }) {
            DialogBadge(Icons.Rounded.CheckCircle, c.pos)
            DialogTitle("Sync complete", message)
            XButton("Done", { viewModel.clearSyncMessage() }, Modifier.fillMaxWidth(), style = BtnStyle.Accent)
        }
    }
}
