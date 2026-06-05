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

@Database(entities = [Expense::class, CategoryRule::class, Category::class], version = 4, exportSchema = false)
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}