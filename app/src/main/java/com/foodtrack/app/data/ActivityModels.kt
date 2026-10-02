package com.foodtrack.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class PhysicalActivity(
    val code: String,
    val met: Double,
    val category: String,
    val description: String
)

@Entity(tableName = "activity_log")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timestamp: Long,
    val activityCode: String,
    val activityName: String,
    val met: Double,
    val durationMinutes: Double,
    val weightKg: Double,
    val caloriesKcal: Double
)
