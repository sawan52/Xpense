package com.example.xpense.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Runs the scheduled automatic Drive backup. Scheduled by [AutoBackupScheduler]; reuses the same
 * [BackupManager.backup] path as the manual "Back up now" button.
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val backupManager = BackupManager(applicationContext)
        // No connected Drive account → nothing to back up. Succeed quietly and stay scheduled;
        // the user may reconnect later.
        if (backupManager.signedInEmail() == null) {
            Log.d(TAG, "Skipping auto-backup: no Google Drive account connected")
            return Result.success()
        }
        return try {
            backupManager.backup()
            Log.d(TAG, "Auto-backup complete")
            Result.success()
        } catch (e: Exception) {
            // Transient network/Drive failures: let WorkManager retry with backoff.
            Log.w(TAG, "Auto-backup failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "XpenseAutoBackup"
    }
}
