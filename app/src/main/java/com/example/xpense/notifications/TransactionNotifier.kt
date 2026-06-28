package com.example.xpense.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.xpense.MainActivity
import com.example.xpense.R
import com.example.xpense.ui.utils.CurrencyUtils
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts a "new uncategorized transaction" notification whose tap opens MainActivity into a
 * pre-filled Add-Rule dialog. The prefs file/key are owned here so the receiver, this notifier,
 * and the ViewModel all read one source of truth for the user's on/off toggle.
 */
object TransactionNotifier {
    const val PREFS_NAME = "xpense_prefs"
    const val KEY_NOTIFICATIONS_ENABLED = "notify_new_transactions"

    private const val CHANNEL_ID = "new_transactions"
    private const val CHANNEL_NAME = "New transactions"

    // Distinct ids/request codes so multiple alerts stack and each carries its own merchant extra.
    private val idCounter = AtomicInteger(1000)

    /** Whether the user has the in-app toggle enabled (defaults on). */
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

    fun notify(context: Context, merchant: String, amount: Double, ignoreToggle: Boolean = false) {
        if (!ignoreToggle && !isEnabled(context)) return
        // API 33+ gates notifications behind a runtime permission; without it posting is a no-op.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(context)

        val id = idCounter.getAndIncrement()
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_RULE_KEYWORD, merchant)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New transaction")
            .setContentText("₹${CurrencyUtils.format(amount, 0)} at $merchant — tap to categorize")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("₹${CurrencyUtils.format(amount, 0)} at $merchant\nTap to create a rule and categorize it.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alerts for transactions that couldn't be auto-categorized" }
        manager.createNotificationChannel(channel)
    }
}
