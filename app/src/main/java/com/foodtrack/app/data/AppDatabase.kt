package com.foodtrack.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FoodEntity::class, FoodPortionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Opens the read-only base food database, which ships prebuilt
         * inside the APK at assets/databases/app_foods.db (see the data
         * pipeline docs — this is the merged, translated USDA subset).
         *
         * NOTE: this DB is currently treated as read-only reference data.
         * User-entered data (custom foods, meal templates, the daily log,
         * weight log) belongs in a *separate* read-write Room database so
         * app updates can safely replace this bundled file without ever
         * touching the user's own data. That second database isn't wired
         * up yet in this skeleton — see README "Next steps".
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_foods.db"
                )
                    .createFromAsset("databases/app_foods.db")
                    .fallbackToDestructiveMigration() // base data is reference-only; safe to rebuild from asset
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
