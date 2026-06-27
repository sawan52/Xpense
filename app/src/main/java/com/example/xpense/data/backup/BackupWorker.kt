package com.example.xpense.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Runs the scheduled automatic Drive backup. Reuses the same [BackupManager.backup] path as the
 * manual "Back up now" button. This is a one-time job that re-arms itself via
 * [AutoBackupScheduler.scheduleNext] after each run, so the schedule stays anchored to ~2 AM
 * instead of drifting (see [AutoBackupScheduler]).
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val backupManager = BackupManager(applicationContext)
        // No connected Drive account → nothing to back up. Re-arm and succeed quietly; the user may
        // reconnect later.
        if (backupManager.signedInEmail() == null) {
            Log.d(TAG, "Skipping auto-backup: no Google Drive account connected")
            AutoBackupScheduler.scheduleNext(applicationContext)
            return Result.success()
        }
        return try {
            backupManager.backup()
            Log.d(TAG, "Auto-backup complete")
            AutoBackupScheduler.scheduleNext(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // Transient network/Drive failures: retry with backoff. After a handful of attempts give
            // up for today but still re-arm tomorrow's run, so one bad night can't kill the schedule.
            if (runAttemptCount >= MAX_ATTEMPTS) {
                Log.w(TAG, "Auto-backup failed after ${runAttemptCount + 1} attempts; re-arming next run", e)
                AutoBackupScheduler.scheduleNext(applicationContext)
                return Result.failure()
            }
            Log.w(TAG, "Auto-backup failed (attempt ${runAttemptCount + 1}), will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "XpenseAutoBackup"
        private const val MAX_ATTEMPTS = 5
    }
}
