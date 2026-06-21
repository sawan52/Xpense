package com.example.xpense.data.backup

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import com.example.xpense.data.database.AppDatabase
import com.example.xpense.data.entity.Category
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import com.google.api.services.drive.model.File as DriveFile

enum class RestoreMode { REPLACE, MERGE }

/** Thrown by backup/restore so the ViewModel can show a precise, user-friendly message. */
sealed class BackupException(message: String) : Exception(message) {
    object NotSignedIn : BackupException("Connect Google Drive first")
    object NoBackupFound : BackupException("No backup found on Google Drive")
}

/**
 * Backs the 3 app tables up to a single JSON file in the user's Google Drive *appDataFolder*
 * (a hidden per-app folder), and restores from it. Going through Room/DAOs — not a raw SQLite
 * file copy — keeps us clear of the WAL-consistency trap and lets Merge remap ids cleanly.
 */
class BackupManager(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val expenseDao = db.expenseDao()
    private val ruleDao = db.categoryRuleDao()
    private val categoryDao = db.categoryDao()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val TAG = "XpenseBackup"
        private const val BACKUP_FILE_NAME = "xpense-backup.json"
        private const val APP_DATA_FOLDER = "appDataFolder"
        // The DB schema version this build writes. Stored in the file so a future importer can
        // migrate older backups. Keep in sync with @Database(version = …) in AppDatabase.
        private const val SCHEMA_VERSION = 9
        private val DRIVE_SCOPE = Scope(DriveScopes.DRIVE_APPDATA)
    }

    // ── Sign-in ────────────────────────────────────────────────────────────────
    private fun signInOptions() = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(DRIVE_SCOPE)
        .build()

    /** Intent the UI launches to pick a Google account and grant the Drive appdata scope. */
    fun signInIntent(): Intent = GoogleSignIn.getClient(context, signInOptions()).signInIntent

    /** Outcome of the account-picker round trip: the email, or a precise failure message. */
    data class SignInResult(val email: String?, val error: String?)

    /** Feed the account-picker result back in. */
    fun handleSignInResult(data: Intent?): SignInResult = try {
        val email = GoogleSignIn.getSignedInAccountFromIntent(data)
            .getResult(ApiException::class.java)?.email
        Log.d(TAG, "Sign-in OK: $email")
        SignInResult(email, null)
    } catch (e: ApiException) {
        Log.w(TAG, "Sign-in failed: statusCode=${e.statusCode} ${e.status}", e)
        // Map the codes that actually happen in the field to actionable messages.
        val hint = when (e.statusCode) {
            10    -> "Developer error (code 10): the OAuth client's package name/SHA-1 don't match this build"
            12501 -> "Sign-in was cancelled"
            12500 -> "Sign-in failed (code 12500): check the OAuth consent screen and test users"
            7     -> "Network error — check your connection"
            else  -> "Sign-in failed (code ${e.statusCode})"
        }
        SignInResult(null, hint)
    } catch (e: Exception) {
        Log.w(TAG, "Sign-in failed", e)
        SignInResult(null, e.message ?: "Sign-in failed")
    }

    fun signedInEmail(): String? = currentAccount()?.email

    suspend fun signOut() {
        GoogleSignIn.getClient(context, signInOptions()).signOut()
    }

    /** The signed-in account, but only if it actually granted the Drive appdata scope. */
    private fun currentAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return if (GoogleSignIn.hasPermissions(account, DRIVE_SCOPE)) account else null
    }

    private fun driveFor(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
        credential.selectedAccount = account.account
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Xpense")
            .build()
    }

    // ── Backup ───────────────────────────────────────────────────────────────────
    suspend fun backup(): Unit = withContext(Dispatchers.IO) {
        val account = currentAccount() ?: throw BackupException.NotSignedIn
        val drive = driveFor(account)

        val data = BackupData(
            schemaVersion = SCHEMA_VERSION,
            appVersionCode = appVersionCode(),
            exportedAt = System.currentTimeMillis(),
            categories = categoryDao.getAllCategoriesList().map { CategoryDto.from(it) },
            rules = ruleDao.getAllRulesList().map { RuleDto.from(it) },
            expenses = expenseDao.getAllExpensesList().map { ExpenseDto.from(it) }
        )
        val content = ByteArrayContent("application/json", json.encodeToString(data).toByteArray())

        val existingId = findBackupFileId(drive)
        if (existingId != null) {
            drive.files().update(existingId, DriveFile(), content).execute()
        } else {
            val meta = DriveFile().setName(BACKUP_FILE_NAME).setParents(listOf(APP_DATA_FOLDER))
            drive.files().create(meta, content).setFields("id").execute()
        }
    }

    /** Epoch-ms of the last backup (Drive file's modifiedTime), or null if none / signed out. */
    suspend fun lastBackupTime(): Long? = withContext(Dispatchers.IO) {
        val account = currentAccount() ?: return@withContext null
        val drive = driveFor(account)
        drive.files().list()
            .setSpaces(APP_DATA_FOLDER)
            .setQ("name = '$BACKUP_FILE_NAME'")
            .setFields("files(id, modifiedTime)")
            .execute()
            .files?.firstOrNull()?.modifiedTime?.value
    }

    // ── Restore ──────────────────────────────────────────────────────────────────
    suspend fun restore(mode: RestoreMode): Unit = withContext(Dispatchers.IO) {
        val account = currentAccount() ?: throw BackupException.NotSignedIn
        val drive = driveFor(account)
        val fileId = findBackupFileId(drive) ?: throw BackupException.NoBackupFound

        val out = ByteArrayOutputStream()
        drive.files().get(fileId).executeMediaAndDownloadTo(out)
        val data = json.decodeFromString<BackupData>(out.toString("UTF-8"))

        when (mode) {
            RestoreMode.REPLACE -> applyReplace(data)
            RestoreMode.MERGE -> applyMerge(data)
        }
    }

    // Wipe everything and write the backup back verbatim. Original ids are preserved, so every
    // expense.categoryId still resolves. One transaction => a failure rolls the whole thing back.
    private suspend fun applyReplace(data: BackupData) {
        db.withTransaction {
            expenseDao.deleteAllExpenses()
            ruleDao.deleteAllRules()
            categoryDao.deleteAllCategories()
            categoryDao.insertCategories(data.categories.map { it.toEntity() })
            ruleDao.insertRules(data.rules.map { it.toEntity() })
            expenseDao.insertExpenses(data.expenses.map { it.toEntity() })
        }
    }

    // Combine the backup with what's already there. Categories are matched by name; unmatched ones
    // are inserted fresh and their new ids folded into the remap. The dedupKey unique index keeps
    // SMS rows from doubling; manual rows (dedupKey = null) can duplicate on repeated merges.
    private suspend fun applyMerge(data: BackupData) {
        db.withTransaction {
            val plan = planMerge(categoryDao.getAllCategoriesList(), data)
            val remap = plan.matchedRemap.toMutableMap()
            plan.categoriesToInsert.forEach { dto ->
                remap[dto.id] = categoryDao.insertCategory(dto.toEntity().copy(id = 0))
            }

            val existingKeywords = ruleDao.getAllRulesList().map { it.keyword.trim().lowercase() }.toSet()
            val rulesToInsert = data.rules
                .filter { it.keyword.trim().lowercase() !in existingKeywords }
                .map { it.toEntity().copy(id = 0, categoryId = remap[it.categoryId] ?: it.categoryId) }
            ruleDao.insertRules(rulesToInsert)

            val expensesToInsert = data.expenses
                .map { it.toEntity().copy(id = 0, categoryId = remap[it.categoryId] ?: it.categoryId) }
            expenseDao.insertExpenses(expensesToInsert)
        }
    }

    // ── Drive helpers ──────────────────────────────────────────────────────────
    private fun findBackupFileId(drive: Drive): String? =
        drive.files().list()
            .setSpaces(APP_DATA_FOLDER)
            .setQ("name = '$BACKUP_FILE_NAME'")
            .setFields("files(id)")
            .execute()
            .files?.firstOrNull()?.id

    private fun appVersionCode(): Int =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
}

/** Categories to create + the id remap for matched ones. */
data class MergePlan(
    val matchedRemap: Map<Long, Long>,        // backup categoryId -> existing live id (matched by name)
    val categoriesToInsert: List<CategoryDto>  // unmatched; inserted fresh, id captured at apply time
)

/**
 * Pure planning step for a Merge restore (no DB / IO so it is unit-testable): match each backup
 * category to an existing one by case-insensitive name. Matches reuse the live id; the rest are
 * queued for insertion. The caller inserts those and extends the remap with their assigned ids.
 */
fun planMerge(existing: List<Category>, backup: BackupData): MergePlan {
    val byName = existing.associateBy { it.name.trim().lowercase() }
    val matched = mutableMapOf<Long, Long>()
    val toInsert = mutableListOf<CategoryDto>()
    backup.categories.forEach { dto ->
        val hit = byName[dto.name.trim().lowercase()]
        if (hit != null) matched[dto.id] = hit.id else toInsert.add(dto)
    }
    return MergePlan(matched, toInsert)
}
