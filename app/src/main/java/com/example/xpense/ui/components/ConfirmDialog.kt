package com.example.xpense.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.xpense.ui.components.design.BtnStyle
import com.example.xpense.ui.components.design.DialogBadge
import com.example.xpense.ui.components.design.DialogButtons
import com.example.xpense.ui.components.design.DialogTitle
import com.example.xpense.ui.components.design.XDialog
import com.example.xpense.ui.theme.XpenseTheme

/**
 * Reusable yes/no confirmation dialog. Guards destructive actions (deleting transactions,
 * auto-rules) so nothing is removed in a single accidental tap. Destructive by default (red badge
 * and button); pass [destructive] = false for neutral confirmations.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector = Icons.Rounded.Delete,
    destructive: Boolean = true
) {
    val c = XpenseTheme.colors
    XDialog(onDismiss) {
        DialogBadge(icon, if (destructive) c.neg else null)
        DialogTitle(title, message)
        DialogButtons(
            confirmText = confirmLabel,
            onConfirm = onConfirm,
            onCancel = onDismiss,
            confirmStyle = if (destructive) BtnStyle.Danger else BtnStyle.Gradient,
            confirmWeight = if (destructive) 1f else 1.4f
        )
    }
}
