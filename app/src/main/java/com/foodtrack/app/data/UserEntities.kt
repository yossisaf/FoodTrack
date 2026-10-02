package com.foodtrack.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-defined food (e.g. "הלחם שלי"), entered manually with per-100g
 * nutrition values, same shape as the bundled reference foods so it can
 * be searched and logged the same way (see FoodSearchResult).
 */
@Entity(tableName = "custom_foods")
data class CustomFoodEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nameHe: String,
    val caloriesKcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val sugarG: Double?,
    val saturatedFatG: Double?,
    val sodiumMg: Double?,
    val cholesterolMg: Double?,
    val createdAt: Long
)

/**
 * One row per food logged on a given day. Nutrition values here are a
 * SNAPSHOT for the actual grams eaten (already scaled), not per-100g —
 * so editing or deleting the source food later never changes past log
 * entries. date is stored as "yyyy-MM-dd" for simple grouping/queries.
 *
 * mealType is nullable and unused today — reserved so that a future
 * "breakfast/lunch/dinner/snack" feature (or meal templates tagging a
 * default meal type) doesn't require a schema migration to add it later.
 */
@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val timestamp: Long,
    val foodSource: String, // "base" or "custom"
    val foodRefId: String,  // food_id (base) or custom_foods.id.toString() (custom)
    val foodNameHe: String,
    val grams: Double,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val mealType: String? = null // reserved for future use, see note above
)

/**
 * Household-measure portions for a user's own custom food (mirrors
 * FoodPortionEntity for the bundled reference DB). Schema exists now;
 * there's no UI yet to add portions when creating a custom food, so
 * this table is empty in practice until that screen is built.
 */
@Entity(tableName = "custom_food_portions")
data class CustomFoodPortionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customFoodId: Long,
    val labelHe: String,
    val grams: Double
)

/**
 * Explicit user "starred" foods — distinct from LogDao.recentDistinctFoods
 * (which is derived automatically from log history). Schema exists now;
 * no UI yet to star/unstar a food.
 */
@Entity(tableName = "favorite_foods")
data class FavoriteFoodEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val foodSource: String,
    val foodRefId: String,
    val foodNameHe: String,
    val addedAt: Long
)

/** A saved meal (e.g. "ארוחת בוקר טיפוסית"), added to the log in one tap. Schema exists now; no builder UI yet. */
@Entity(tableName = "meal_templates")
data class MealTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nameHe: String,
    val createdAt: Long
)

/** One food within a meal template. foodNameHe is a snapshot so the template still shows a name even if the source food is later deleted. */
@Entity(tableName = "meal_template_items")
data class MealTemplateItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val foodSource: String,
    val foodRefId: String,
    val foodNameHe: String,
    val grams: Double
)

@Entity(tableName = "weight_log")
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val timestamp: Long,
    val weightKg: Double
)

/**
 * One row per "cup of water" logged. Counting rows (grouped by date) is
 * simpler and avoids upsert logic compared to a single running-total
 * column, and naturally supports "undo last cup" by deleting the most
 * recent row for today.
 */
@Entity(tableName = "water_log")
data class WaterEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val timestamp: Long
)
