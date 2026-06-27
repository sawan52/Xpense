package com.example.xpense.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.xpense.notifications.TransactionNotifier
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** How often the automatic Drive backup runs (OFF = disabled, the default). */
enum class AutoBackupFrequency { OFF, DAILY, WEEKLY, MONTHLY }

/**
 * Owns the auto-backup setting and its WorkManager schedule, so the ViewModel and the worker read
 * one source of truth. The cadence is persisted in the shared "xpense_prefs" file.
 *
 * Timing is implemented as a **self-rescheduling one-time job**, NOT a [androidx.work.PeriodicWorkRequest].
 * A periodic request only honours an initial delay for its first run; every later run is scheduled one
 * interval after the *previous actual* run, so once the OS defers the first 2 AM run (Doze + the
 * periodic flex window) the whole schedule drifts away from 2 AM and never comes back. Instead, after
 * each run [BackupWorker] calls [scheduleNext], which recomputes the next 2 AM — so every run
 * re-anchors to ~2 AM. (Still approximate: Doze can push it a bit past 2 AM, but it can no longer
 * drift cumulatively. Exact-to-the-minute would need exact-alarm permission, overkill for backups.)
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

    /**
     * Re-establish the schedule from the persisted setting. Call on app start: it's idempotent
     * (re-anchors to the next 2 AM) and migrates any older periodic schedule to the one-time job,
     * since [enqueueUniqueWork]/[cancelUniqueWork] act on the unique name regardless of work type.
     */
    fun reapply(context: Context) = apply(context, getFrequency(context))

    /** Chain the next run, re-anchored to the next 2 AM. Called by the worker after each run. */
    fun scheduleNext(context: Context) {
        val frequency = getFrequency(context)
        if (frequency == AutoBackupFrequency.OFF) return
        enqueue(context, frequency)
    }

    private fun apply(context: Context, frequency: AutoBackupFrequency) {
        if (frequency == AutoBackupFrequency.OFF) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        enqueue(context, frequency)
    }

    private fun enqueue(context: Context, frequency: AutoBackupFrequency) {
        val intervalDays = when (frequency) {
            AutoBackupFrequency.DAILY -> 1L
            AutoBackupFrequency.WEEKLY -> 7L
            AutoBackupFrequency.MONTHLY -> 30L
            AutoBackupFrequency.OFF -> return // unreachable
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayUntilNextRun(intervalDays), TimeUnit.MILLISECONDS)
            .build()

        // REPLACE so re-anchoring / changing the cadence supersedes the pending job instead of
        // stacking. A worker rescheduling itself here is fine — it's already finishing.
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Milliseconds until the next 2 AM that is [intervalDays] cadence away (today's/next 2 AM for daily). */
    private fun delayUntilNextRun(intervalDays: Long): Long =
        delayToNext2am() + TimeUnit.DAYS.toMillis(intervalDays - 1)

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
