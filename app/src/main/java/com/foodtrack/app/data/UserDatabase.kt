package com.foodtrack.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * User's own data: custom foods, the daily log, weight, water. This is a
 * completely separate database file from AppDatabase (the bundled
 * read-only reference DB) — a normal Room database created fresh on
 * device, NOT copied from an asset. This separation matters: when the
 * app is updated with a new app_foods.db (more translated categories),
 * that update must never touch this file or the user would lose their
 * logged data.
 *
 * DEV-PHASE NOTE (see plan doc / chat history): fallbackToDestructiveMigration()
 * is enabled below ONLY because there are no real users yet — it lets
 * schema changes during active development (new tables/columns) just wipe
 * and recreate this DB instead of crashing with an identity-hash mismatch.
 * This MUST be replaced with real Room Migration objects (and this
 * fallback removed) before this app ships to real users — otherwise a
 * future schema change would silently delete everyone's logged data.
 * Every time an entity here changes, bump `version` below.
 */
@Database(
    entities = [
        CustomFoodEntity::class,
        CustomFoodPortionEntity::class,
        LogEntryEntity::class,
        WeightEntryEntity::class,
        WaterEventEntity::class,
        FavoriteFoodEntity::class,
        MealTemplateEntity::class,
        MealTemplateItemEntity::class,
        ActivityLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun customFoodDao(): CustomFoodDao
    abstract fun customFoodPortionDao(): CustomFoodPortionDao
    abstract fun logDao(): LogDao
    abstract fun weightDao(): WeightDao
    abstract fun waterDao(): WaterDao
    abstract fun favoriteFoodDao(): FavoriteFoodDao
    abstract fun mealTemplateDao(): MealTemplateDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // v2 added user-owned favorites and saved meal templates.
                // Existing log/custom-food/weight/water data is preserved.
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `favorite_foods` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `foodSource` TEXT NOT NULL,
                        `foodRefId` TEXT NOT NULL,
                        `foodNameHe` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `meal_templates` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `nameHe` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `meal_template_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `templateId` INTEGER NOT NULL,
                        `foodSource` TEXT NOT NULL,
                        `foodRefId` TEXT NOT NULL,
                        `foodNameHe` TEXT NOT NULL,
                        `grams` REAL NOT NULL
                    )
                """.trimIndent())
            }
        }


        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `activity_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `activityCode` TEXT NOT NULL,
                        `activityName` TEXT NOT NULL,
                        `met` REAL NOT NULL,
                        `durationMinutes` REAL NOT NULL,
                        `weightKg` REAL NOT NULL,
                        `caloriesKcal` REAL NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Version 3 changes migration policy only; the schema is unchanged.
            }
        }

        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getInstance(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "user_data.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
