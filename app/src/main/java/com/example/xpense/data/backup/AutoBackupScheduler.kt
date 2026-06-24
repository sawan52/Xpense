package com.example.xpense.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.xpense.notifications.TransactionNotifier
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** How often the automatic Drive backup runs (OFF = disabled, the default). */
enum class AutoBackupFrequency { OFF, DAILY, WEEKLY, MONTHLY }

/**
 * Owns the auto-backup setting and its WorkManager schedule, so the ViewModel and the worker read
 * one source of truth. The cadence is persisted in the shared "xpense_prefs" file; the actual work
 * is a network-constrained periodic job that fires around 2 AM (see [delayToNext2am]).
 */
object AutoBackupScheduler {
    const val KEY_AUTO_BACKUP_FREQ = "auto_backup_frequency"
    private const val WORK_NAME = "xpense_auto_backup"
    private const val BACKUP_HOUR = 2 // 2 AM, local time

    fun getFrequency(context: Context): AutoBackupFrequency {
        val name = context.getSharedPreferences(TransactionNotifier.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTO_BACKUP_FREQ, null) ?: return AutoBackupFrequency.OFF
        return runCatching { AutoBackupFrequency.valueOf(name) }.getOrDefault(AutoBackupFrequency.OFF)
    }

    /** Persist the chosen cadence and (re)schedule or cancel the background job accordingly. */
    fun setFrequency(context: Context, frequency: AutoBackupFrequency) {
        context.getSharedPreferences(TransactionNotifier.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AUTO_BACKUP_FREQ, frequency.name)
            .apply()
        apply(context, frequency)
    }

    private fun apply(context: Context, frequency: AutoBackupFrequency) {
        val workManager = WorkManager.getInstance(context)
        if (frequency == AutoBackupFrequency.OFF) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val intervalDays = when (frequency) {
            AutoBackupFrequency.DAILY -> 1L
            AutoBackupFrequency.WEEKLY -> 7L
            AutoBackupFrequency.MONTHLY -> 30L
            AutoBackupFrequency.OFF -> return // unreachable
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<BackupWorker>(intervalDays, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(delayToNext2am(), TimeUnit.MILLISECONDS)
            .build()

        // UPDATE so changing the cadence replaces the existing schedule rather than stacking jobs.
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Milliseconds from now until the next local 2 AM. */
    private fun delayToNext2am(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, BACKUP_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
