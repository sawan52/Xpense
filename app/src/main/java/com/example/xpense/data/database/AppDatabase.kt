package com.example.xpense.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.xpense.data.dao.ExpenseDao
import com.example.xpense.data.dao.CategoryRuleDao
import com.example.xpense.data.dao.CategoryDao
import com.example.xpense.data.entity.Expense
import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.data.entity.Category

@Database(entities = [Expense::class, CategoryRule::class, Category::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Adds the optional rule "label" (display name) column. Non-destructive so existing
        // expenses/rules survive the upgrade (unlike the fallbackToDestructiveMigration path).
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE category_rules ADD COLUMN label TEXT")
            }
        }

        // Enforce no-duplicate SMS transactions at the DB level. Adds a nullable dedupKey
        // (= SMS body for SMS rows, null for manual rows), cleans up any pre-existing
        // duplicates, then adds a unique index. NULLs are allowed multiple times, so manual
        // entries stay unconstrained. Index name must match Room's expected index_expenses_dedupKey.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN dedupKey TEXT")
                db.execSQL(
                    "UPDATE expenses SET dedupKey = rawSms " +
                        "WHERE rawSms NOT IN ('Manual Entry','Manual Update')"
                )
                db.execSQL(
                    "DELETE FROM expenses WHERE dedupKey IS NOT NULL AND id NOT IN (" +
                        "SELECT MIN(id) FROM expenses WHERE dedupKey IS NOT NULL GROUP BY dedupKey)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_expenses_dedupKey ON expenses(dedupKey)")
            }
        }

        // Adds the "ignored" flag (0/1) so users can exclude non-expense rows (e.g. self-transfers)
        // from totals. Non-destructive; existing rows default to not-ignored.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN ignored INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}